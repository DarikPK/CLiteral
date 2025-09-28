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
import org.json.JSONArray

class ExtractionFragment : Fragment() {

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
        val autofillButton = binding.autofillButton
        binding.extractButton.visibility = if (url?.contains(resultsUrlSubstring) == true) View.VISIBLE else View.GONE

        when {
            url == loginUrl -> {
                autofillButton.visibility = View.VISIBLE
                val redColor = ContextCompat.getColor(requireContext(), R.color.button_red)
                autofillButton.backgroundTintList = ColorStateList.valueOf(redColor)
            }
            url?.startsWith(searchUrl) == true -> {
                autofillButton.visibility = View.VISIBLE
                val blueColor = ContextCompat.getColor(requireContext(), R.color.button_blue)
                autofillButton.backgroundTintList = ColorStateList.valueOf(blueColor)
            }
            else -> {
                autofillButton.visibility = View.GONE
            }
        }
    }

    private fun setupButtons() {
        binding.autofillButton.setOnClickListener {
            updateButtonStates(binding.webView.url)
            autofillCurrentPage()
        }
        binding.extractButton.setOnClickListener {
            extractImagesFromWebView()
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
                // Waits for an element to exist in the DOM.
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

                // Waits for an element to be enabled (not disabled).
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

                // Clicks a dropdown, scrolls to the option, and selects it.
                async function selectDropdownOption(dropdownSelector, optionTitle, predefinedList) {
                    const dropdown = await waitForElementEnabled(dropdownSelector);
                    dropdown.click();
                    await new Promise(resolve => setTimeout(resolve, 300));

                    const targetIndex = predefinedList.indexOf(optionTitle);
                    if (targetIndex === -1) {
                        console.error(`Option "${'$'}{optionTitle}" not found in list.`);
                        return false;
                    }

                    const scrollViewport = document.querySelector('.cdk-virtual-scroll-viewport');
                    if (scrollViewport) {
                        const itemHeight = 32;
                        scrollViewport.scrollTo({ top: targetIndex * itemHeight, behavior: 'auto' });
                        await new Promise(resolve => setTimeout(resolve, 300));
                    }

                    const optionToClick = await waitForElement(`nz-option-item[title="${'$'}{optionTitle}"] .ant-select-item-option-content`);
                    optionToClick.click();
                    return true;
                }

                // --- Main Execution ---
                const officeList = ["ABANCAY", "ANDAHUAYLAS", "AREQUIPA", "AYACUCHO", "BAGUA", "BARRANCA", "CAJAMARCA", "CALLAO", "CAMANA", "CASMA", "CASTILLA _ APLAO", "CAÑETE", "CHACHAPOYAS", "CHEPEN", "CHICLAYO", "CHIMBOTE", "CHINCHA", "CUSCO", "HUACHO", "HUANCAVELICA", "HUANCAYO", "HUANUCO", "HUARAL", "HUARAZ", "ICA", "IQUITOS", "JAEN", "JAUJA", "JULIACA", "LA MERCED", "LIMA", "LORETO", "MADRE DE DIOS", "MOLLENDO", "MOQUEGUA", "MOYOBAMBA", "NASCA", "OXAPAMPA", "PACASMAYO", "PASCO", "PISCO", "PIURA", "PUCALLPA", "PUNO", "QUILLABAMBA", "SATIPO", "SICUANI", "SULLANA", "TACNA", "TARAPOTO", "TARMA", "TUMBES", "YURIMAGUAS"];
                const areaList = ["REGISTRO DE PREDIOS", "REGISTRO DE PERSONAS JURIDICAS", "REGISTRO DE PERSONAS NATURALES", "REGISTRO DE BIENES MUEBLES"];

                // 1. Select "Oficina Registral"
                await selectDropdownOption('nz-select[formcontrolname="oficinaRegistral"]', '${config.oficina}', officeList);

                // 2. Wait for "Área Registral" to be enabled, then select it
                await waitForElementEnabled('nz-select[formcontrolname="areaRegistral"]');
                await selectDropdownOption('nz-select[formcontrolname="areaRegistral"]', '${config.areaRegistral}', areaList);

                // 3. Wait for the "Partida" radio button to be enabled, then click it
                const partidaRadio = await waitForElementEnabled('label[nzvalue="2"] input');
                partidaRadio.click();

                // 4. Wait for the number input to be enabled, then fill it
                const numeroInput = await waitForElementEnabled('input[formcontrolname="numero"]');
                numeroInput.value = '${config.numeroPartida}';
                numeroInput.dispatchEvent(new Event('input', { bubbles: true }));
                numeroInput.dispatchEvent(new Event('blur', { bubbles: true }));

                // 5. Wait for the search button to be enabled, then click it
                const submitButton = await waitForElementEnabled('button.btn-buscar-partida');
                submitButton.click();

            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript, null)
    }

    private fun extractImagesFromWebView() {
        val jsScript = """
            (function() {
                const pageUrl = window.location.href;
                const urls = Array.from(document.querySelectorAll('img')).map(img => {
                    let src = img.getAttribute('src') || img.getAttribute('data-src');
                    if (!src) return null;
                    try { return new URL(src, pageUrl).href; } catch (e) { return null; }
                }).filter(Boolean);
                return JSON.stringify(urls);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript) { result ->
            try {
                val jsonArray = JSONArray(result)
                val imageUrls = List(jsonArray.length()) { i -> jsonArray.getString(i) }
                sharedViewModel.setImageUrls(imageUrls)
                activity?.runOnUiThread {
                    Toast.makeText(context, "${imageUrls.size} imágenes extraídas y guardadas.", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.mainMenuFragment, false)
                }
            } catch (e: Exception) {
                activity?.runOnUiThread { Toast.makeText(context, "Error al procesar las imágenes.", Toast.LENGTH_LONG).show() }
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}