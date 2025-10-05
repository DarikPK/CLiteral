package com.example.imageextractor

import android.os.Bundle
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
    private var currentZoom = 1.0

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
                // Reset zoom on new page load
                currentZoom = 1.0
                applyZoom(currentZoom)
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
                binding.debugButton.visibility = View.VISIBLE
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
            increaseResolution()
        }
        binding.extractButton.setOnClickListener(null)
    }

    private fun increaseResolution() {
        currentZoom *= 1.5
        applyZoom(currentZoom)
    }

    private fun applyZoom(zoomLevel: Double) {
        val script = "document.body.style.zoom='${zoomLevel}'"
        binding.webView.evaluateJavascript(script) {
            activity?.runOnUiThread {
                Toast.makeText(context, "Resolución aumentada al ${(zoomLevel * 100).toInt()}%", Toast.LENGTH_SHORT).show()
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
                            reject(new Error(`Element with selector "\${'$'}{selector}" not found within \${'$'}{timeout}ms`));
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
                  throw new Error(`Opción "\${'$'}{optionText}" no encontrada en el menú desplegable dentro de \${'$'}{timeout}ms`);
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
                            console.error(`Option "\${'$'}{optionTitle}" not found in list.`);
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
                        console.error(`Failed to select dropdown option "\${'$'}{optionTitle}":`, e);
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
                          reject(new Error(`Element with selector "\${'$'}{selector}" not found within \${'$'}{timeout}ms`));
                      }, timeout);
                  });
              }

              async function waitForCanvasStable(timeout = 10000) {
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
                      if (Date.now() - stableSince >= 600) {
                        return Array.from(canvases);
                      }
                    }
                  }
                  await sleep(200);
                }
                return Array.from(document.querySelectorAll('canvas'));
              }

              async function waitForVisibleCanvas(timeout = 10000) {
                  const start = Date.now();
                  while (Date.now() - start < timeout) {
                      const canvas = document.querySelector('canvas');
                      if (canvas && canvas.height > 0 && canvas.width > 0) {
                          return canvas;
                      }
                      await sleep(200);
                  }
                  throw new Error('Visible canvas not found within timeout');
              }

              async function openAsientoIfCollapsed(asientoElem) {
                const header = asientoElem.querySelector('.ant-collapse-header');
                if (header && !asientoElem.classList.contains('ant-collapse-item-active')) {
                  header.click();
                  await new Promise(r => setTimeout(r, 500));
                }
              }

              async function captureCanvasesAndGetData(asientoNum, paginaNum) {
                const canvases = Array.from(document.querySelectorAll('canvas'));
                const capturedImages = [];
                for (let i = 0; i < canvases.length; i++) {
                  try {
                    const canvas = canvases[i];
                    const dataUrl = canvas.toDataURL("image/png");
                    const filename = `asiento_\${'$'}{asientoNum}_pagina_\${'$'}{paginaNum}_canvas_\${'$'}{i+1}.png`;
                    capturedImages.push({ filename: filename, dataUrl: dataUrl });
                    console.log(`Asiento \${'$'}{asientoNum} - Página \${'$'}{paginaNum} - Canvas \${'$'}{i+1} capturado.`);
                  } catch (err) {
                    console.error(`Error capturando canvas \${'$'}{i+1} de asiento \${'$'}{asientoNum} página \${'$'}{paginaNum}:`, err);
                  }
                }
                return capturedImages;
              }

              console.log("Inicio recorrido asientos/páginas...");

              try {
                console.log("Intentando expandir el menú de asientos (robusto)...");

                // 1) Intentar encontrar el SVG del ícono de menú y subir al botón contenedor.
                let collapseButton = null;
                const svgMenu = document.querySelector('svg[data-icon="menu"], svg.anticon-menu');
                if (svgMenu) {
                  collapseButton = svgMenu.closest('button, a, div, span');
                }

                // 2) Si no hubo SVG, intentar selector directo clásico
                if (!collapseButton) {
                  collapseButton = document.querySelector('button.ant-btn.collapse-button');
                }

                // 3) Fallback: buscar por clases del span/icon
                if (!collapseButton) {
                  const spanIcon = document.querySelector('span.anticon.anticon-menu, span[nz-icon]');
                  if (spanIcon) collapseButton = spanIcon.closest('button, a, div, span');
                }

                // 4) Si aún no hay botón, log y continuar (no bloquear)
                if (!collapseButton) {
                  console.warn("No se encontró el botón para expandir el menú de asientos (fallbacks). Continuando sin expandir.");
                } else {
                  // Función robusta de click (simula eventos pointer + mouse si click simple falla)
                  function robustClick(el) {
                    try {
                      el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
                      el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
                      el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                      el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                      return true;
                    } catch (err) {
                      try { el.click(); return true; } catch (_) { return false; }
                    }
                  }

                  const didClick = robustClick(collapseButton);
                  // Dar un poco más de tiempo a la apertura (algunas páginas Angular tardan)
                  try {
                    await waitForElement('.ant-collapse-item', 8000);
                    console.log("Menú de asientos expandido (detected .ant-collapse-item).");
                  } catch (waitErr) {
                    // Si no aparece .ant-collapse-item, pero hicimos click, intentamos detectar presencia de lista
                    const fallbackDetected = document.querySelector('.columna-lista, .menu-asientos, .ant-collapse-item');
                    if (fallbackDetected) {
                      console.log("Menú de asientos expandido (fallback detectado).");
                    } else {
                      console.warn("No se detectó apertura del menú tras el click. didClick=", didClick);
                    }
                  }
                }
              } catch (e) {
                console.error("Error al intentar expandir el menú de asientos (robusto):", e);
              }

              const allImageData = [];
              let X = 1;

              while (true) {
                const asientoElem = Array.from(document.querySelectorAll('.columna-lista'))
                  .find(el => (el.innerText || "").includes(`N° Asiento: \${'$'}{X}`));

                if (!asientoElem) {
                  console.log(`No se encontró Asiento \${'$'}{X}. Fin del recorrido.`);
                  break;
                }

                await openAsientoIfCollapsed(asientoElem);

                console.log(`Procesando Asiento \${'$'}{X}...`);
                let Y = 1;

                while (true) {
                  const asientoElemCurrent = Array.from(document.querySelectorAll('.columna-lista'))
                    .find(el => (el.innerText || "").includes(`N° Asiento: \${'$'}{X}`));

                  if (!asientoElemCurrent) {
                    console.warn(`El Asiento \${'$'}{X} desapareció; salir de sus páginas.`);
                    break;
                  }

                  const botonY = Array.from(asientoElemCurrent.querySelectorAll('.pagina .boton-pagina'))
                    .find(span => (span.textContent || span.innerText || "").trim() === `\${'$'}{Y}`);

                  if (!botonY) {
                    console.log(`No se encontró página \${'$'}{Y} en Asiento \${'$'}{X} — pasar a Asiento \${'$'}{X+1}.`);
                    break;
                  }

                  try {
                    console.log(`Asiento \${'$'}{X} -> Página \${'$'}{Y}: clic...`);
                    botonY.click();
                  } catch (err) {
                    try {
                      botonY.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    } catch (innerErr) {
                      console.error(`No se pudo hacer click en Asiento \${'$'}{X} Página \${'$'}{Y}:`, innerErr);
                    }
                  }

                  // Force viewer container to be visible and wait for a visible canvas
                  try {
                      const pdfViewerContainer = document.querySelector('.pdfViewer, .ng2-pdf-viewer-container');
                      if (pdfViewerContainer) {
                          pdfViewerContainer.style.height = "1000px";
                          pdfViewerContainer.style.display = "block";
                      }

                      await waitForVisibleCanvas(10000);
                      const canvases = await waitForCanvasStable();

                      if (!canvases || canvases.length === 0) {
                           console.warn(`En Asiento \${'$'}{X} Página \${'$'}{Y} no se detectaron <canvas> estables. Continuando.`);
                      } else {
                          const imagesData = await captureCanvasesAndGetData(X, Y);
                          allImageData.push(...imagesData);
                          console.log(`Asiento \${'$'}{X} Página \${'$'}{Y}: \${'$'}{imagesData.length} canvas capturados.`);
                      }
                  } catch (e) {
                       console.warn(`En Asiento \${'$'}{X} Página \${'$'}{Y} no se detectó canvas visible (timeout): `, e.message);
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
                    Toast.makeText(context, "\${savedImagePaths.size} imágenes guardadas exitosamente.", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.mainMenuFragment, false)

                } catch (e: Exception) {
                    Toast.makeText(context, "Error al procesar o guardar las imágenes: \${e.message}", Toast.LENGTH_LONG).show()
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