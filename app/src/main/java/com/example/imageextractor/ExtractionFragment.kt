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
                Toast.makeText(context, "URL: $url", Toast.LENGTH_LONG).show()
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
            autofillCurrentPage()
        }
        binding.extractButton.setOnClickListener {
            extractImagesFromWebView()
        }
    }

    private fun autofillCurrentPage() {
        when {
            currentPageUrl == loginUrl -> {
                val newLoginData = sharedViewModel.getRandomLoginData()
                sharedViewModel.config.value?.let {
                    sharedViewModel.setExtractionConfig(it.copy(loginData = newLoginData))
                }
                autofillLoginForm(newLoginData)
            }
            currentPageUrl?.startsWith(searchUrl) == true -> {
                sharedViewModel.config.value?.let {
                    autofillSearchForm(it)
                } ?: Toast.makeText(context, "No hay configuración de búsqueda guardada.", Toast.LENGTH_SHORT).show()
            }
            else -> Toast.makeText(context, "No hay formulario para autocompletar.", Toast.LENGTH_SHORT).show()
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
        // Script mejorado con async/await para esperar que los elementos existan.
        val jsScript = """
            (async function() {
                // Función para esperar a que un elemento aparezca en el DOM.
                function waitForElement(selector) {
                    return new Promise(resolve => {
                        const interval = setInterval(() => {
                            const element = document.querySelector(selector);
                            if (element) {
                                clearInterval(interval);
                                resolve(element);
                            }
                        }, 100); // Revisa cada 100ms
                    });
                }

                // Función para simular un clic y seleccionar una opción de un menú desplegable.
                async function selectDropdown(formControlName, value) {
                    const dropdown = await waitForElement(`nz-select[formcontrolname='${'$'}{formControlName}']`);
                    dropdown.click();
                    const option = await waitForElement(`.ant-select-item-option-content[title="${'$'}{value}"]`);
                    option.click();
                }

                await selectDropdown('oficina', '${config.oficina}');
                await selectDropdown('areaRegistral', '${config.areaRegistral}');

                const numeroInput = await waitForElement('input[formcontrolname="numero"]');
                numeroInput.value = '${config.numeroPartida}';
                numeroInput.dispatchEvent(new Event('input', { bubbles: true }));

                const radio = await waitForElement('.ant-radio-input');
                radio.click();

                const submitButton = await waitForElement('button[type="submit"]');
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