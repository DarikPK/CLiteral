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
import androidx.appcompat.app.AlertDialog
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
        binding.debugButton.setOnClickListener {
            showCurrentUrlDialog()
        }
    }

    private fun showCurrentUrlDialog() {
        val currentUrl = binding.webView.url
        AlertDialog.Builder(requireContext())
            .setTitle("URL Actual")
            .setMessage(currentUrl ?: "No hay URL disponible")
            .setPositiveButton("Copiar") { dialog, _ ->
                val clipboard = context?.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("URL", currentUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "URL copiada al portapapeles", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
            (function() {
                let report = '--- Informe de Depuración de Selectores ---\\n\\n';
                const selectors = {
                    'Dropdown Oficina': 'nz-select[formcontrolname=\"oficina\"]',
                    'Dropdown Área Registral': 'nz-select[formcontrolname=\"areaRegistral\"]',
                    'Input Número de Partida': 'input[formcontrolname=\"numero\"]',
                    'Botón de Radio': '.ant-radio-input',
                    'Botón de Envío': 'button[type=\"submit\"]'
                };

                for (const [name, selector] of Object.entries(selectors)) {
                    const element = document.querySelector(selector);
                    report += `Buscando '${'$'}{name}'...\\n`;
                    report += `Selector: ${'$'}{selector}\\n`;
                    report += `Encontrado: ${'$'}{element ? 'SÍ' : 'NO'}\\n\\n`;
                }

                report += '--- Nota ---\\n';
                report += 'Si los dropdowns son encontrados pero el autocompletado falla, el problema puede estar en los selectores de las *opciones* que aparecen después de hacer clic.';

                return report;
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(jsScript) { result ->
            activity?.runOnUiThread {
                val cleanResult = result?.removeSurrounding("\"")?.replace("\\n", "\n")
                AlertDialog.Builder(requireContext())
                    .setTitle("Informe de Depuración del Script")
                    .setMessage(cleanResult ?: "No se recibió respuesta del script.")
                    .setPositiveButton("Cerrar") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
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