package com.example.imageextractor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentExtractionBinding
import org.json.JSONArray

class ExtractionFragment : Fragment() {

    private var _binding: FragmentExtractionBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Máquina de estados para controlar el proceso de automatización.
    private enum class AutomationState { IDLE, LOGIN_PAGE, SEARCH_PAGE, RESULTS_PAGE }
    private var currentState = AutomationState.IDLE

    // URLs para la navegación y detección de estado.
    private val loginUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/inicio"
    private val searchUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/servicio/busqueda"
    private val resultsUrlSubstring = "/servicio/busqueda/visualizar-partida"

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
        setupExtractButton()
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                handleAutomationStep(url) // Orquesta la automatización.
            }
        }
        // Inicia el proceso cargando la página de login.
        currentState = AutomationState.LOGIN_PAGE
        binding.webView.loadUrl(loginUrl)
    }

    private fun handleAutomationStep(url: String?) {
        val config = sharedViewModel.config.value ?: return

        when (currentState) {
            AutomationState.LOGIN_PAGE -> {
                if (url == loginUrl) {
                    autofillLoginForm(config.loginData)
                    currentState = AutomationState.SEARCH_PAGE
                }
            }
            AutomationState.SEARCH_PAGE -> {
                if (url?.startsWith(searchUrl) == true) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        autofillSearchForm(config)
                        currentState = AutomationState.RESULTS_PAGE
                    }, 2500) // Espera para asegurar que los scripts de la página carguen.
                }
            }
            AutomationState.RESULTS_PAGE -> {
                if (url?.contains(resultsUrlSubstring) == true) {
                    binding.extractButton.visibility = View.VISIBLE
                    Toast.makeText(context, "Automatización completada. Listo para extraer.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> { /* No hacer nada en estado IDLE */ }
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
                document.querySelector('button[type="submit"]').click();
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript, null)
    }

    private fun autofillSearchForm(config: ExtractionConfig) {
        val jsScript = """
            (function() {
                function selectDropdown(formControlName, value) {
                    const dropdown = document.querySelector(`nz-select[formcontrolname='${'$'}{formControlName}']`);
                    if (dropdown) {
                        dropdown.click();
                        setTimeout(() => {
                            const options = document.querySelectorAll('.ant-select-item-option-content');
                            const option = Array.from(options).find(opt => opt.textContent.trim() === value);
                            if (option) option.click();
                        }, 500);
                    }
                }
                selectDropdown('oficina', '${config.oficina}');
                setTimeout(() => {
                    selectDropdown('areaRegistral', '${config.areaRegistral}');
                    setTimeout(() => {
                        document.querySelector('input[formcontrolname="numero"]').value = '${config.numeroPartida}';
                        document.querySelector('input[formcontrolname="numero"]').dispatchEvent(new Event('input', { bubbles: true }));
                        document.querySelector('.ant-radio-input').click();
                        document.querySelector('button[type="submit"]').click();
                    }, 1000);
                }, 1000);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(jsScript, null)
    }

    private fun setupExtractButton() {
        binding.extractButton.setOnClickListener {
            extractImagesFromWebView()
        }
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