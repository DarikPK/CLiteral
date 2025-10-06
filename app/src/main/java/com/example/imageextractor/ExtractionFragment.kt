package com.example.imageextractor

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
        fun notifyHeaderSelected(text: String) {
            Log.d("JsBridge", "Header seleccionado: $text")
        }

        @JavascriptInterface
        fun onSelectorCaptured(selector: String) {
            Log.d("JsBridge", "Selector capturado: $selector")
        }
    }

    private var _binding: FragmentExtractionBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val loginUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/inicio"
    private val searchUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/servicio/busqueda"
    private val resultsUrlSubstring = "/servicio/busqueda/visualizar-partida"
    private var currentPageUrl: String? = null
    private var extractionTriggered = false

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

                if (url?.contains(resultsUrlSubstring) == true && !extractionTriggered) {
                    extractionTriggered = true
                    Toast.makeText(context, "Página de partida detectada, iniciando extracción automática...", Toast.LENGTH_SHORT).show()
                    view?.postDelayed({ extractImagesFromPartida() }, 2000)
                }
            }
        }
        binding.webView.loadUrl(loginUrl)
    }

    private fun updateButtonStates(url: String?) {
        val actionButton = binding.actionButton
        binding.extractButton.visibility = View.GONE // This button is no longer used

        when {
            url?.contains(resultsUrlSubstring) == true -> {
                // Extraction is now automatic, so hide the action button
                actionButton.visibility = View.GONE
                binding.debugButton.visibility = View.VISIBLE
                binding.fabGoToFirst.visibility = View.VISIBLE
                binding.fabGoToLast.visibility = View.VISIBLE
                // Set initial state: user starts at the last page
                binding.fabGoToFirst.isEnabled = true
                binding.fabGoToLast.isEnabled = false
            }
            url == loginUrl || url?.startsWith(searchUrl) == true -> {
                actionButton.visibility = View.VISIBLE
                actionButton.setImageResource(android.R.drawable.ic_menu_edit)
                actionButton.contentDescription = "Autocompletar Datos"
                binding.debugButton.visibility = View.GONE
            }
            else -> {
                actionButton.visibility = View.GONE
                binding.debugButton.visibility = View.GONE
            }
        }
    }

    private fun setupButtons() {
        binding.actionButton.setOnClickListener {
            when {
                currentPageUrl?.contains(resultsUrlSubstring) == true -> {
                    extractImagesFromPartida()
                }
                else -> {
                    autofillCurrentPage()
                }
            }
        }
        binding.debugButton.setOnClickListener {
            showSidePanel()
        }
        binding.extractButton.setOnClickListener(null)

        binding.fabGoToFirst.setOnClickListener { navigateToFirst() }
        binding.fabGoToLast.setOnClickListener { navigateToLast() }
    }

    private fun navigateToFirst() {
        val script = """
            (function() {
                function realisticClick(element) {
                    try {
                        const events = ['mousedown', 'mouseup', 'click'];
                        events.forEach(type => element.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true })));
                        return true;
                    } catch (e) { return false; }
                }
                const firstPageButton = document.querySelector('.pagina .boton-pagina');
                if (firstPageButton) {
                    return realisticClick(firstPageButton);
                }
                return false;
            })();
        """
        binding.webView.evaluateJavascript(script) { result ->
            activity?.runOnUiThread {
                if (result == "true") {
                    binding.fabGoToFirst.isEnabled = false
                    binding.fabGoToLast.isEnabled = true
                } else {
                    Toast.makeText(context, "No se pudo ir a la primera hoja", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToLast() {
        val script = """
            (function() {
                 function realisticClick(element) {
                    try {
                        const events = ['mousedown', 'mouseup', 'click'];
                        events.forEach(type => element.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true })));
                        return true;
                    } catch (e) { return false; }
                }
                const pageButtons = document.querySelectorAll('.pagina .boton-pagina');
                if (pageButtons.length > 0) {
                    const lastPageButton = pageButtons[pageButtons.length - 1];
                    return realisticClick(lastPageButton);
                }
                return false;
            })();
        """
        binding.webView.evaluateJavascript(script) { result ->
            activity?.runOnUiThread {
                if (result == "true") {
                    binding.fabGoToFirst.isEnabled = true
                    binding.fabGoToLast.isEnabled = false
                } else {
                    Toast.makeText(context, "No se pudo ir a la última hoja", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSidePanel() {
        val script = """
            (function() {
                const sideBar = document.querySelector('.side-bar-container');
                if (sideBar) {
                    sideBar.style.display = 'block';
                    sideBar.classList.add('side-bar-no-collapsed');
                    sideBar.classList.remove('side-bar-collapsed');
                }
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
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
                } ?: Toast.makeText(context, "No hay configuración de búsqueda guardada.", Toast.LENGTH_SHORT).show()
            }
            else -> Toast.makeText(context, "No hay formulario para autocompletar en esta página.", Toast.LENGTH_SHORT).show()
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

                function waitForElement(selector, timeout = 5000, scope = document) {
                    return new Promise((resolve, reject) => {
                        const interval = setInterval(() => {
                            const element = scope.querySelector(selector);
                            if (element) {
                                clearInterval(interval);
                                resolve(element);
                            }
                        }, 100);
                        setTimeout(() => {
                            clearInterval(interval);
                            reject(new Error(`Element with selector "${'$'}{selector}" not found within ${'$'}{timeout}ms`));
                        }, timeout);
                    });
                }

                async function waitForOptionByText(optionText, timeout = 5000) {
                  const start = Date.now();
                  while (Date.now() - start < timeout) {
                    const options = document.querySelectorAll('.ant-select-item-option-content');
                    for (const opt of options) {
                      if ((opt.textContent || "").trim().toUpperCase() === optionText.toUpperCase()) {
                        return opt;
                      }
                    }
                    await new Promise(r => setTimeout(r, 100));
                  }
                  throw new Error(`Opción "${'$'}{optionText}" no encontrada en el menú desplegable dentro de ${'$'}{timeout}ms`);
                }

                async function selectDropdownOption(dropdownSelector, optionTitle, predefinedList) {
                    try {
                        const dropdown = await waitForElement(dropdownSelector);
                        const clickable = dropdown.querySelector('.ant-select-selector') || dropdown;

                        // Hacer clic para abrir el menú
                        clickable.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                        clickable.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));

                        // Esperar a que el panel de superposición (overlay) esté visible
                        const overlayContainer = await waitForElement('.cdk-overlay-container .ant-select-dropdown', 5000);

                        const targetIndex = predefinedList.indexOf(optionTitle);
                        if (targetIndex === -1) {
                            console.error(`Option "${'$'}{optionTitle}" not found in list.`);
                            return false;
                        }

                        // Hacer scroll dentro del viewport del overlay
                        const scrollViewport = overlayContainer.querySelector('.cdk-virtual-scroll-viewport');
                        if (scrollViewport) {
                            const itemHeight = 32;
                            scrollViewport.scrollTo({ top: targetIndex * itemHeight, behavior: 'auto' });
                            await sleep(300); // Dar tiempo para que el scroll termine
                        }

                        // Seleccionar la opción usando el texto visible
                        const optionToClick = await waitForOptionByText(optionTitle);
                        optionToClick.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                        optionToClick.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                        await sleep(300); // Pausa para que se registre la selección
                        return true;

                    } catch(e) {
                        console.error(`Failed to select dropdown option "${'$'}{optionTitle}":`, e);
                        return false;
                    }
                }

                const officeList = ["ABANCAY", "ANDAHUAYLAS", "AREQUIPA", "AYACUCHO", "BAGUA", "BARRANCA", "CAJAMARCA", "CALLAO", "CAMANA", "CASMA", "CASTILLA _ APLAO", "CAÑETE", "CHACHAPOYAS", "CHEPEN", "CHICLAYO", "CHIMBOTE", "CHINCHA", "CUSCO", "HUACHO", "HUANCAVELICA", "HUANCAYO", "HUANUCO", "HUARAL", "HUARAZ", "ICA", "IQUITOS", "JAEN", "JAUJA", "JULIACA", "LA MERCED", "LIMA", "LORETO", "MADRE DE DIOS", "MOLLENDO", "MOQUEGUA", "MOYOBAMBA", "NASCA", "OXAPAMPA", "PACASMAYO", "PASCO", "PISCO", "PIURA", "PUCALLPA", "PUNO", "QUILLABAMBA", "SATIPO", "SICUANI", "SULLANA", "TACNA", "TARAPOTO", "TARMA", "TUMBES", "YURIMAGUAS"];
                const newAreaList = ["PROPIEDAD INMUEBLE PREDIAL", "PROPIEDAD INMUEBLE NO PREDIAL", "PERSONAS JURIDICAS", "PERSONAS NATURALES", "PROPIEDAD VEHICULAR", "PROPIEDAD MINERIA", "REGISTRO DE NAVES Y EMBARCACIONES (ANTES REGISTRO DE EMBARCACIONES PESQUERAS)", "PROPIEDAD AERONAVES", "REGISTRO MOBILIARIO DE CONTRATOS", "REGISTRO DE NAVES Y EMBARCACIONES (ANTES REGISTRO DE NAVES)"];
                const areaMapping = {
                    "REGISTRO DE PREDIOS": "PROPIEDAD INMUEBLE PREDIAL",
                    "REGISTRO DE PERSONAS JURIDICAS": "PERSONAS JURIDICAS",
                    "REGISTRO DE PERSONAS NATURALES": "PERSONAS NATURALES",
                    "REGISTRO DE BIENES MUEBLES": "REGISTRO MOBILIARIO DE CONTRATOS"
                };
                const mappedAreaTitle = areaMapping['${config.areaRegistral}'] || '${config.areaRegistral}';

                await selectDropdownOption('nz-select[formcontrolname="oficinaRegistral"]', '${config.oficina}', officeList);
                await selectDropdownOption('nz-select[formcontrolname="areaRegistral"]', mappedAreaTitle, newAreaList);

                const partidaRadio = await waitForElement('label[nzvalue="2"] input');
                partidaRadio.click();

                const numeroInput = await waitForElement('input[formcontrolname="numero"]');
                numeroInput.value = '${config.numeroPartida}';
                numeroInput.dispatchEvent(new Event('input', { bubbles: true }));
                numeroInput.dispatchEvent(new Event('blur', { bubbles: true }));

                const submitButton = await waitForElement('button.btn-buscar-partida');
                submitButton.click();

                const previewButton = await waitForElement('button[title="Previsualizar"].btn-search', 10000);
                previewButton.click();

                await waitForElement('.columna-lista', 10000);
                if (typeof AndroidBridge !== 'undefined') {
                    AndroidBridge.notifyUrlChanged();
                }
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript, null)
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
                            await sleep(300); // Dar un respiro extra para el renderizado final
                            return canvas;
                        }
                        await sleep(100);
                    }
                    throw new Error(`Canvas visible no encontrado en ${'$'}{timeout}ms`);
                }

                console.log("Iniciando recorrido de asientos y páginas...");
                const allImageData = [];
                const asientos = Array.from(document.querySelectorAll('.columna-lista .ant-collapse-item'));

                for (let i = 0; i < asientos.length; i++) {
                    const asiento = asientos[i];
                    const asientoNumber = i + 1;

                    console.log(`Procesando Asiento ${'$'}{asientoNumber}...`);

                    // Expandir si está colapsado
                    if (!asiento.classList.contains('ant-collapse-item-active')) {
                        const header = asiento.querySelector('.ant-collapse-header');
                        if (header) {
                            console.log(` -> Asiento ${'$'}{asientoNumber} está colapsado, expandiendo...`);
                            realisticClick(header);
                            await sleep(500); // Pausa para la animación
                        }
                    }

                    const pageButtons = Array.from(asiento.querySelectorAll('.pagina .boton-pagina'));

                    for (let j = 0; j < pageButtons.length; j++) {
                        const button = pageButtons[j];
                        const pageNumber = (button.textContent || "").trim();

                        if (button.offsetParent === null) {
                            console.log(` -> Página ${'$'}{pageNumber} en Asiento ${'$'}{asientoNumber} no está visible, saltando.`);
                            continue;
                        }

                        console.log(` -> Procesando Página ${'$'}{pageNumber}...`);
                        realisticClick(button);
                        await sleep(600); // Pausa crítica para el renderizado

                        try {
                            await waitForVisibleCanvas();
                            const canvases = Array.from(document.querySelectorAll('canvas')).filter(c => c.offsetParent !== null);

                            for (let k = 0; k < canvases.length; k++) {
                                const canvas = canvases[k];
                                const dataUrl = canvas.toDataURL("image/png");
                                const filename = `asiento_${'$'}{asientoNumber}_pagina_${'$'}{pageNumber}_canvas_${'$'}{k + 1}.png`;
                                allImageData.push({ filename, dataUrl });
                                console.log(`   -> Canvas ${'$'}{k + 1} de Página ${'$'}{pageNumber} (Asiento ${'$'}{asientoNumber}) capturado.`);
                            }
                        } catch (e) {
                            console.error(`Error procesando Página ${'$'}{pageNumber} en Asiento ${'$'}{asientoNumber}: ${'$'}{e.message}`);
                        }
                    }
                }

                console.log("✅ Recorrido completo.");
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
            val imageDir = File(downloadsDir, "extacciones")
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