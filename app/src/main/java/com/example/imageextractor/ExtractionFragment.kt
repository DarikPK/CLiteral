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
    private val toastHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var toastRunnable: Runnable? = null

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
                injectSpaUrlWatcher()
            }
        }

        binding.webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (mimeType == "image/png" && url.startsWith("data:")) {
                handleDataUrlDownload(url, contentDisposition)
            }
        }

        binding.webView.loadUrl(loginUrl)
    }

    private fun handleDataUrlDownload(url: String, contentDisposition: String) {
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "capturas_sunarp"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val fileName = contentDisposition
                .substringAfter("filename=\"", "")
                .substringBefore("\"")
                .takeIf { it.isNotEmpty() } ?: generarNombreArchivo()

            val file = File(downloadDir, fileName)

            val base64EncodedString = url.substring(url.indexOf(",") + 1)
            val decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT)

            FileOutputStream(file).use { it.write(decodedBytes) }

            toastRunnable?.let { toastHandler.removeCallbacks(it) }
            toastRunnable = Runnable {
                Toast.makeText(context, "Captura(s) guardada(s) en Descargas/capturas_sunarp", Toast.LENGTH_LONG).show()
            }
            toastHandler.postDelayed(toastRunnable!!, 500)

        } catch (e: Exception) {
            Log.e("WebViewDownload", "Error al guardar captura desde data URL", e)
            Toast.makeText(context, "Error al guardar la captura.", Toast.LENGTH_LONG).show()
        }
    }

    private fun injectSpaUrlWatcher() {
        val script = """
            (function() {
              if (window.__androidUrlHooked) return;
              window.__androidUrlHooked = true;
              function notify(){ try { AndroidBridge && AndroidBridge.notifyUrlChanged(); } catch(e){} }
              var pushState = history.pushState;
              history.pushState = function(){ pushState.apply(this, arguments); setTimeout(notify, 0); };
              var replaceState = history.replaceState;
              history.replaceState = function(){ replaceState.apply(this, arguments); setTimeout(notify, 0); };
              window.addEventListener('popstate', notify, true);
              window.addEventListener('hashchange', notify, true);
              // Notificación inicial por si ya estamos en una subruta
              setTimeout(notify, 0);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
    }

    private fun isResultsPage(url: String?) =
        url?.contains(resultsUrlSubstring, ignoreCase = true) == true

    private fun isSearchPage(url: String?) =
        url?.startsWith(searchUrl, ignoreCase = true) == true && !isResultsPage(url)

    private fun updateButtonStates(url: String?) {
        if (isViewDestroyed) return

        val onLoginPage = url == loginUrl
        val onSearchPage = isSearchPage(url)
        val onResultsPage = isResultsPage(url)

        binding.autofillButton.visibility = if (onLoginPage || onSearchPage) View.VISIBLE else View.GONE
        binding.captureButton.visibility = if (onResultsPage) View.VISIBLE else View.GONE
        binding.fabListButton.visibility = if (onResultsPage) View.VISIBLE else View.GONE

        val navigationVisible = if (onResultsPage) View.VISIBLE else View.GONE
        binding.fabGoToFirstItem.visibility = navigationVisible
        binding.fabGoToLastItem.visibility = navigationVisible
        binding.fabPrevious.visibility = navigationVisible
        binding.fabNext.visibility = navigationVisible

        binding.extractButton.visibility = View.GONE // Keep it hidden as per original logic

        if (onResultsPage) {
            // Reset navigation buttons to initial state when entering results page
            binding.fabGoToLastItem.isEnabled = false
            binding.fabNext.isEnabled = false
            binding.fabGoToFirstItem.isEnabled = true
            binding.fabPrevious.isEnabled = true
        }
    }

    private fun setupButtons() {
        binding.autofillButton.setOnClickListener {
            autofillCurrentPage()
        }

        binding.fabListButton.setOnClickListener {
            showPageListOverlay()
        }

        binding.captureButton.setOnClickListener {
            captureVisibleCanvas()
        }

        binding.extractButton.setOnClickListener { extractImagesFromPartida() }

        binding.fabGoToFirstItem.setOnClickListener { navigateTo("first") }
        binding.fabGoToLastItem.setOnClickListener { navigateTo("last") }
        binding.fabPrevious.setOnClickListener { navigateTo("previous") }
        binding.fabNext.setOnClickListener { navigateTo("next") }
    }

    private fun generarNombreArchivo(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        return "captura_sunarp_$timestamp.png"
    }

    private fun captureVisibleCanvas() {
        val script = """
        (function() {
          const canvases = document.querySelectorAll('canvas:not([style*="display: none"])');
          if (!canvases.length) {
            console.log("No hay canvas para capturar");
            return;
          }
          canvases.forEach((canvas, i) => {
            try {
              const dataUrl = canvas.toDataURL("image/png");
              const a = document.createElement("a");
              a.href = dataUrl;
              a.download = "captura_" + Date.now() + "_" + (i+1) + ".png";
              document.body.appendChild(a);
              a.click();
              document.body.removeChild(a);
            } catch (e) {
              console.error("Error al capturar canvas: ", e);
            }
          });
        })();
        """.trimIndent()

        // loadUrl is compatible with all API levels for this fire-and-forget script.
        binding.webView.loadUrl("javascript:$script")
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

    private fun showPageListOverlay() {
        val script = """
(function() {
    // ID único para el overlay, para evitar duplicados si se pulsa el botón varias veces
    const OVERLAY_ID = 'sunarp-lista-overlay';
    if (document.getElementById(OVERLAY_ID)) {
        // Si ya existe, simplemente lo muestra en lugar de recrearlo
        document.getElementById(OVERLAY_ID).style.display = 'flex';
        return;
    }

    const container = document.querySelector('.columna-lista');
    if (!container) {
        console.error('El contenedor de la lista (.columna-lista) aún no está disponible. Por favor, espere a que la lista de asientos/tomos cargue y vuelva a intentarlo.');
        // Opcional: Mostrar un Toast al usuario
        // AndroidBridge.showToast("La lista de páginas aún no está disponible.");
        return;
    }

    async function robustClick(element, timeout = 5000) {
        if (!element) {
            console.warn('robustClick: Elemento no proporcionado.');
            return false;
        }
        const start = Date.now();
        while (Date.now() - start < timeout) {
            if (!document.body.contains(element)) {
                 console.error('robustClick: El elemento ya no está en el DOM.');
                 return false;
            }
            const style = window.getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            if (element.offsetParent !== null && !element.disabled && !element.hasAttribute('disabled') && style.pointerEvents !== 'none' && rect.width > 0 && rect.height > 0) {
                const centerX = rect.left + rect.width / 2;
                const centerY = rect.top + rect.height / 2;
                const elementAtCenter = document.elementFromPoint(centerX, centerY);
                if (elementAtCenter && (elementAtCenter === element || element.contains(elementAtCenter))) {
                    try {
                        element.scrollIntoView({ block: 'center', inline: 'center' });
                        await sleep(150);
                        element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                        await sleep(50);
                        element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                        await sleep(50);
                        element.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                        return true;
                    } catch (e) {
                        console.error('robustClick: Falló el evento de clic, reintentando...', e);
                    }
                }
            }
            await sleep(200);
        }
        console.error('robustClick: No se pudo hacer clic en el elemento después de ' + (timeout / 1000) + 's.', element);
        return false;
    }

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    function hideListOverlay() {
        const overlay = document.getElementById(OVERLAY_ID);
        if (overlay) overlay.style.display = 'none';
    }

    const allPages = [];
    const sections = container.querySelectorAll(':scope > .ant-collapse > .ant-collapse-item, :scope > div.ant-collapse-item');
    sections.forEach((section, sectionIndex) => {
        const header = section.querySelector('.ant-collapse-header');
        const headerText = header ? (header.innerText || '').trim() : '';

        let type = 'Sección';
        let sectionNum = sectionIndex;
        const asientoMatch = headerText.match(/Asiento\\s+N°:\\s*(\\d+)/i);
        const tomoMatch = headerText.match(/Tomo:\\s*(\\d+)/i);
        if (asientoMatch) { type = 'Asiento'; sectionNum = parseInt(asientoMatch[1], 10); }
        else if (tomoMatch) { type = 'Tomo'; sectionNum = parseInt(tomoMatch[1], 10); }

        const pageButtons = section.querySelectorAll('.pagina .boton-pagina, .pagina a, a.boton-pagina');

        if (pageButtons.length > 0) {
            pageButtons.forEach((btn, pageIndex) => {
                const btnText = (btn.innerText || '').trim();
                let pageLabel = `Pág. ${'$'}{pageIndex + 1}`;
                let pageNum = pageIndex;
                const folioMatch = btnText.match(/Folio:\\s*(\\d+)/i) || btnText.match(/F:\\s*(\\d+)/i);
                const pageMatch = btnText.match(/Página:\\s*(\\d+)/i) || btnText.match(/P:\\s*(\\d+)/i);
                if (folioMatch) { pageLabel = `Folio ${'$'}{folioMatch[1]}`; pageNum = parseInt(folioMatch[1], 10); }
                else if(pageMatch) { pageLabel = `Página ${'$'}{pageMatch[1]}`; pageNum = parseInt(pageMatch[1], 10); }

                allPages.push({
                    element: btn, sectionHeader: header, isCollapsed: !section.classList.contains('ant-collapse-item-active'),
                    sectionNum, pageNum, fullLabel: `${'$'}{type} ${'$'}{asientoMatch || tomoMatch ? sectionNum : ''} - ${'$'}{pageLabel}`
                });
            });
        } else {
            allPages.push({
                element: header, sectionHeader: header, isCollapsed: false,
                sectionNum, pageNum: 0, fullLabel: `${'$'}{headerText} (Página única)`
            });
        }
    });

    allPages.sort((a, b) => a.sectionNum !== b.sectionNum ? a.sectionNum - b.sectionNum : a.pageNum - b.pageNum);

    const overlay = document.createElement('div');
    overlay.id = OVERLAY_ID;
    Object.assign(overlay.style, {
        position: 'fixed', top: '0', left: '0', width: '100vw', height: '100vh',
        backgroundColor: 'rgba(0, 0, 0, 0.6)', zIndex: '10000', display: 'flex',
        justifyContent: 'center', alignItems: 'center'
    });

    overlay.innerHTML = `
        <div id="sunarp-lista-panel" style="background: white; border-radius: 8px; width: 90%; max-width: 600px; max-height: 85vh; display: flex; flex-direction: column; box-shadow: 0 5px 15px rgba(0,0,0,0.3);">
            <div style="padding: 12px 16px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center;">
                <h3 id="sunarp-lista-total" style="margin: 0; font-size: 16px;">Total páginas: ${'$'}{allPages.length}</h3>
                <button id="sunarp-lista-close" style="background: transparent; border: none; font-size: 24px; cursor: pointer; padding: 0 8px;">&times;</button>
            </div>
            <div id="sunarp-lista-items" style="overflow-y: auto; padding: 8px;"></div>
        </div>
    `;
    document.body.appendChild(overlay);

    const listContainer = overlay.querySelector('#sunarp-lista-items');
    allPages.forEach((pageInfo, index) => {
        const itemDiv = document.createElement('div');
        itemDiv.textContent = `Elemento ${'$'}{index + 1}: ${'$'}{pageInfo.fullLabel}`;
        Object.assign(itemDiv.style, { padding: '10px 16px', borderBottom: '1px solid #f0f0f0', cursor: 'pointer' });
        itemDiv.onmouseenter = () => itemDiv.style.backgroundColor = '#f7f7f7';
        itemDiv.onmouseleave = () => itemDiv.style.backgroundColor = 'transparent';

        itemDiv.onclick = async () => {
            hideListOverlay();
            await sleep(100);
            if (pageInfo.isCollapsed && pageInfo.sectionHeader) {
                await robustClick(pageInfo.sectionHeader);
                await sleep(400);
            }
            await robustClick(pageInfo.element);
        };
        listContainer.appendChild(itemDiv);
    });

    overlay.querySelector('#sunarp-lista-close').onclick = hideListOverlay;
    overlay.onclick = e => { if (e.target.id === OVERLAY_ID) hideListOverlay(); };

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
            currentUrl?.startsWith(searchUrl) == true && !currentUrl.contains(resultsUrlSubstring) -> {
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
      function sleep(ms){ return new Promise(r=>setTimeout(r,ms)); }

      function waitForElement(sel, t=8000, scope=document){
        return new Promise((res,rej)=>{
          const ts = Date.now();
          const it = setInterval(()=>{
            const el = scope.querySelector(sel);
            if (el && el.offsetParent !== null) { clearInterval(it); res(el); }
            else if (Date.now()-ts>t){ clearInterval(it); rej(new Error("No se encontró "+sel)); }
          },100);
        });
      }

      async function robustClick(element, timeout = 5000) {
          const start = Date.now();
          while (Date.now() - start < timeout) {
              if (element && element.offsetParent !== null && !element.disabled) {
                  element.scrollIntoView({block: 'center'});
                  await sleep(200);
                  element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                  element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                  element.dispatchEvent(new MouseEvent('click', { bubbles: true }));
                  return true;
              }
              await sleep(100);
          }
          throw new Error('No se pudo hacer clic en el elemento de forma robusta.');
      }

      async function waitForOptionAndScroll(optionText, container, timeout = 8000) {
          const start = Date.now();
          const viewport = container.querySelector('.cdk-virtual-scroll-viewport');

          // Primero, intentar encontrar la opción sin scrollear, por si ya está visible
          let options = Array.from(container.querySelectorAll('.ant-select-item-option-content'));
          let foundOption = options.find(o => (o.textContent || "").trim().toUpperCase() === optionText.toUpperCase());
          if (foundOption) return foundOption;

          // Si no está visible y hay un viewport virtual, scrollear
          if (viewport) {
              // Obtener todas las opciones para calcular el índice correcto
              const allOptionsText = ${sharedViewModel.getOfficeListJson()}; // Asume que tienes una lista completa
              const optionIndex = allOptionsText.indexOf(optionText.toUpperCase());

              if (optionIndex !== -1) {
                  const itemHeight = 32; // Altura estándar de un item en antd
                  viewport.scrollTo({ top: optionIndex * itemHeight, behavior: 'auto' });
                  await sleep(400); // Esperar a que el scroll termine y se renderice
              }
          }

          // Volver a buscar la opción después del scroll
          const finalOptions = Array.from(container.querySelectorAll('.ant-select-item-option-content'));
          foundOption = finalOptions.find(o => (o.textContent || "").trim().toUpperCase() === optionText.toUpperCase());
          if (foundOption) return foundOption;

          throw new Error("Opción '" + optionText + "' no encontrada en el dropdown.");
      }

      async function selectDropdown(dropdownSel, optionText){
        const dd = await waitForElement(dropdownSel);
        await robustClick(dd.querySelector('.ant-select-selector') || dd);

        const overlay = await waitForElement('.cdk-overlay-container .ant-select-dropdown', 6000);
        const opt = await waitForOptionAndScroll(optionText, overlay);

        await robustClick(opt);
        await sleep(300);
      }

      const areaMap = {
        "REGISTRO DE PREDIOS": "PROPIEDAD INMUEBLE PREDIAL",
        "REGISTRO DE PERSONAS JURIDICAS": "PERSONAS JURIDICAS",
        "REGISTRO DE PERSONAS NATURALES": "PERSONAS NATURALES",
        "REGISTRO DE BIENES MUEBLES": "REGISTRO MOBILIARIO DE CONTRATOS"
      };
      const mappedArea = areaMap["${config.areaRegistral}"] || "${config.areaRegistral}";

      await selectDropdown('nz-select[formcontrolname="oficinaRegistral"]', "${config.oficina}");
      await selectDropdown('nz-select[formcontrolname="areaRegistral"]', mappedArea);

      const partidaRadio = await waitForElement('label[nzvalue="2"] input');
      await robustClick(partidaRadio);

      const numero = await waitForElement('input[formcontrolname="numero"]');
      numero.value = "${config.numeroPartida}";
      numero.dispatchEvent(new Event('input',{bubbles:true}));
      numero.dispatchEvent(new Event('blur',{bubbles:true}));

      const buscarBtn = await waitForElement('button.btn-buscar-partida');
      await robustClick(buscarBtn);

      async function robustClickPreview(timeout = 15000) {
        const start = Date.now();
        const selector = 'button[title="Previsualizar"].btn-search, button.btn-search[title="Previsualizar"]';

        while (Date.now() - start < timeout) {
            if (document.querySelector('.cdk-overlay-backdrop') || document.querySelector('.ant-select-dropdown')) {
                await sleep(200);
                continue;
            }

            const buttons = Array.from(document.querySelectorAll(selector));
            let targetButton = null;

            for (const btn of buttons) {
                const style = window.getComputedStyle(btn);
                const rect = btn.getBoundingClientRect();

                if (
                    btn.offsetParent !== null &&
                    !btn.disabled &&
                    style.pointerEvents !== 'none' &&
                    rect.width > 0 && rect.height > 0
                ) {
                    const centerX = rect.left + rect.width / 2;
                    const centerY = rect.top + rect.height / 2;
                    const elementAtCenter = document.elementFromPoint(centerX, centerY);

                    if (elementAtCenter && (elementAtCenter === btn || btn.contains(elementAtCenter))) {
                        targetButton = btn;
                        break;
                    }
                }
            }

            if (targetButton) {
                try {
                    targetButton.scrollIntoView({ block: 'center' });
                    await sleep(100);
                    targetButton.focus();
                    await sleep(100);

                    ['mousedown', 'mouseup', 'click'].forEach(type => {
                        targetButton.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
                    });
                    return; // Success
                } catch (e) {
                    console.error("Click en Previsualizar falló, reintentando...", e);
                }
            }
            await sleep(250);
        }
        throw new Error("No se pudo hacer clic en Previsualizar después de " + (timeout / 1000) + "s.");
      }

      await robustClickPreview(15000);

      await waitForElement('.columna-lista', 15000);
      try { AndroidBridge && AndroidBridge.notifyUrlChanged(); } catch(e){}
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