package com.example.imageextractor

import android.os.Bundle
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

    private val loginUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/inicio"
    private val targetUrlSubstring = "/servicio/busqueda/visualizar-partida"

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
                binding.extractButton.isEnabled = url?.contains(targetUrlSubstring) == true
                // El botón de autocompletar solo debe estar activo en la página de inicio.
                binding.autofillButton.isEnabled = url == loginUrl
            }
        }
        binding.webView.loadUrl(loginUrl)
    }

    private fun setupButtons() {
        binding.extractButton.setOnClickListener {
            extractImagesFromWebView()
        }
        binding.autofillButton.setOnClickListener {
            autofillLoginForm()
        }
    }

    private fun autofillLoginForm() {
        val jsScript = """
            (function() {
                function fillInput(selector, value) {
                    let element = document.querySelector(selector);
                    if (element) {
                        element.value = value;
                        // Disparamos eventos para que el framework de la página reconozca el cambio.
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                }

                fillInput('input[formcontrolname="numeroDocumento"]', '46736604');
                fillInput('input[formcontrolname="digito"]', '7');
                fillInput('input[formcontrolname="fechaEmision"]', '16/04/2025');

                return "Datos autocompletados.";
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(jsScript) { result ->
            activity?.runOnUiThread {
                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
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
                    findNavController().popBackStack()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error al procesar las imágenes.", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}