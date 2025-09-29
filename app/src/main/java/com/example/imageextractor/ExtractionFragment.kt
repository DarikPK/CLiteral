package com.example.imageextractor

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentExtractionBinding
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.webkit.JavascriptInterface

class ExtractionFragment : Fragment() {

    private inner class JsBridge {
        @JavascriptInterface
        fun notifyUrlChanged() {
            activity?.runOnUiThread {
                updateButtonStates(binding.webView.url)
            }
        }
    }

    private var _binding: FragmentExtractionBinding? = null
    private val binding get() = _binding!!

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
        val actionButton = binding.actionButton
        binding.extractButton.visibility = View.GONE // This button is no longer used

        when {
            url?.contains(resultsUrlSubstring) == true -> {
                actionButton.visibility = View.VISIBLE
                actionButton.setImageResource(android.R.drawable.ic_media_play)
                actionButton.contentDescription = "Iniciar extracción de imágenes"
            }
            url == loginUrl || url?.startsWith(searchUrl) == true -> {
                actionButton.visibility = View.VISIBLE
                actionButton.setImageResource(android.R.drawable.ic_menu_edit)
                actionButton.contentDescription = "Autocompletar Datos"
            }
            else -> {
                actionButton.visibility = View.GONE
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
        // The old extract button logic is removed, its functionality is now in actionButton.
        binding.extractButton.setOnClickListener(null)
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
                function waitForElement(selector, timeout = 5000) {
                    return new Promise((resolve, reject) => {
                        const interval = setInterval(() => {
                            const element = document.querySelector(selector);
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

                async function waitForElementEnabled(selector, timeout = 5000) {
                    const element = await waitForElement(selector, timeout);
                    return new Promise((resolve, reject) => {
                        const interval = setInterval(() => {
                            const isNzSelectDisabled = element.classList.contains('ant-select-disabled');
                            const isInputDisabled = element.disabled;
                            if (!isNzSelectDisabled && !isInputDisabled) {
                                clearInterval(interval);
                                resolve(element);
                            }
                        }, 100);
                        setTimeout(() => {
                            clearInterval(interval);
                            reject(new Error(`Element "${'$'}{selector}" did not become enabled within ${'$'}{timeout}ms`));
                        }, timeout);
                    });
                }

                async function selectDropdownOption(dropdownSelector, optionTitle, predefinedList) {
                    try {
                        const dropdownHost = await waitForElementEnabled(dropdownSelector);

                        // More robust click simulation to open the dropdown overlay
                        dropdownHost.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                        await sleep(50);
                        dropdownHost.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                        await sleep(50);
                        dropdownHost.click();
                        await sleep(500); // Wait for overlay animation

                        // Use a more robust selector for the virtual scroll viewport
                        const scrollViewport = document.querySelector('body .cdk-virtual-scroll-viewport');
                        if (scrollViewport) {
                            const targetIndex = predefinedList.indexOf(optionTitle);
                            if (targetIndex > -1) {
                                const itemHeight = 32; // Common height for dropdown items
                                scrollViewport.scrollTo({ top: targetIndex * itemHeight, behavior: 'auto' });
                                await sleep(500); // Wait for scroll to finish
                            }
                        }

                        // Wait for the desired option to be present in the DOM and click it.
                        // Using the title attribute is more reliable than textContent.
                        const optionSelector = `nz-option-item[title="${optionTitle}"]`;
                        const optionToClick = await waitForElement(optionSelector, 5000);

                        if (optionToClick) {
                            optionToClick.click();
                            await sleep(300); // Wait for selection to be processed
                            return true;
                        } else {
                            console.error(`Dropdown option "${optionTitle}" not found after scroll and wait.`);
                            return false;
                        }
                    } catch (error) {
                        console.error(`Error selecting dropdown option "${optionTitle}":`, error);
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
                await waitForElementEnabled('nz-select[formcontrolname="areaRegistral"]');
                await selectDropdownOption('nz-select[formcontrolname="areaRegistral"]', mappedAreaTitle, newAreaList);

                const partidaRadio = await waitForElementEnabled('label[nzvalue="2"] input');
                partidaRadio.click();

                const numeroInput = await waitForElementEnabled('input[formcontrolname="numero"]');
                numeroInput.value = '${config.numeroPartida}';
                numeroInput.dispatchEvent(new Event('input', { bubbles: true }));
                numeroInput.dispatchEvent(new Event('blur', { bubbles: true }));

                const submitButton = await waitForElementEnabled('button.btn-buscar-partida');
                submitButton.click();

                const previewButton = await waitForElement('button[title="Previsualizar"].btn-search', 10000);
                previewButton.click();

                // Wait for the next page to load and notify Android to update the button state
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
              // --- Configuración ---
              const MAX_WAIT_MS = 10000;
              const STABLE_CHECK_MS = 600;
              const POLL_INTERVAL_MS = 200;

              // --- Helpers ---
              function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

              async function waitForCanvasStable(timeout = MAX_WAIT_MS) {
                const start = Date.now();
                let lastCount = 0;
                let stableSince = Date.now();

                while (Date.now() - start < timeout) {
                  const canvases = document.querySelectorAll('canvas');
                  const count = canvases.length;

                  if (count > 0) {
                    if (count !== lastCount) {
                      stableSince = Date.now();
                      lastCount = count;
                    } else {
                      if (Date.now() - stableSince >= STABLE_CHECK_MS) {
                        return Array.from(canvases);
                      }
                    }
                  }
                  await sleep(POLL_INTERVAL_MS);
                }
                return Array.from(document.querySelectorAll('canvas'));
              }

              async function captureCanvasesAndGetData(asientoNum, paginaNum) {
                const canvases = Array.from(document.querySelectorAll('canvas'));
                const capturedImages = [];
                for (let i = 0; i < canvases.length; i++) {
                  try {
                    const canvas = canvases[i];
                    const dataUrl = canvas.toDataURL("image/png");
                    const filename = `asiento_${"$"}{asientoNum}_pagina_${"$"}{paginaNum}_canvas_${"$"}{i+1}.png`;
                    capturedImages.push({ filename: filename, dataUrl: dataUrl });
                    console.log(`Asiento ${"$"}{asientoNum} - Página ${"$"}{paginaNum} - Canvas ${"$"}{i+1} capturado.`);
                  } catch (err) {
                    console.error(`Error capturando canvas ${"$"}{i+1} de asiento ${"$"}{asientoNum} página ${"$"}{paginaNum}:`, err);
                  }
                }
                return capturedImages;
              }

              // --- Lógica Principal ---
              console.log("Inicio recorrido asientos/páginas...");
              const allImageData = [];
              let X = 1;

              while (true) {
                const asientoElem = Array.from(document.querySelectorAll('.columna-lista'))
                  .find(el => (el.innerText || "").includes(`N° Asiento: ${"$"}{X}`));

                if (!asientoElem) {
                  console.log(`No se encontró Asiento ${"$"}{X}. Fin del recorrido.`);
                  break;
                }

                console.log(`Procesando Asiento ${"$"}{X}...`);
                let Y = 1;

                while (true) {
                  const asientoElemCurrent = Array.from(document.querySelectorAll('.columna-lista'))
                    .find(el => (el.innerText || "").includes(`N° Asiento: ${"$"}{X}`));

                  if (!asientoElemCurrent) {
                    console.warn(`El Asiento ${"$"}{X} desapareció; salir de sus páginas.`);
                    break;
                  }

                  const botonY = Array.from(asientoElemCurrent.querySelectorAll('.pagina .boton-pagina'))
                    .find(span => (span.textContent || span.innerText || "").trim() === `${"$"}{Y}`);

                  if (!botonY) {
                    console.log(`No se encontró página ${"$"}{Y} en Asiento ${"$"}{X} — pasar a Asiento ${"$"}{X+1}.`);
                    break;
                  }

                  try {
                    console.log(`Asiento ${"$"}{X} -> Página ${"$"}{Y}: clic...`);
                    botonY.click();
                  } catch (err) {
                    try {
                      botonY.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    } catch (innerErr) {
                      console.error(`No se pudo hacer click en Asiento ${"$"}{X} Página ${"$"}{Y}:`, innerErr);
                    }
                  }

                  const canvases = await waitForCanvasStable(MAX_WAIT_MS);
                  if (!canvases || canvases.length === 0) {
                    console.warn(`En Asiento ${"$"}{X} Página ${"$"}{Y} no se detectaron <canvas>. Continuando.`);
                  } else {
                    const imagesData = await captureCanvasesAndGetData(X, Y);
                    allImageData.push(...imagesData);
                    console.log(`Asiento ${"$"}{X} Página ${"$"}{Y}: ${"$"}{imagesData.length} canvas capturados.`);
                  }

                  Y++;
                  await sleep(300);
                }

                X++;
                await sleep(400);
              }

              console.log("✅ Recorrido completo de todos los asientos y páginas.");
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
                    Toast.makeText(context, "${savedImagePaths.size} imágenes guardadas exitosamente.", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.mainMenuFragment, false)

                } catch (e: Exception) {
                    Toast.makeText(context, "Error al procesar o guardar las imágenes: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveImageFromDataUrl(dataUrl: String, filename: String): String? {
        return try {
            val imageDir = File(context?.getExternalFilesDir(null), "extacciones")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            val imageFile = File(imageDir, filename)

            // data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...
            val base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1)
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)

            FileOutputStream(imageFile).use { out ->
                out.write(decodedBytes)
            }
            imageFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}