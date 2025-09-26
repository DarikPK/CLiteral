package com.example.imageextractor

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.drawToBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.ActivityMainBinding
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter

    private val loginUrl = "https://conoce-aqui.sunarp.gob.pe/conoce-aqui/inicio"
    private val targetUrlSubstring = "/servicio/busqueda/visualizar-partida"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupRecyclerView()
        setupButtonListeners()
        setupSeekBarListeners()
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.extractButton.isEnabled = url?.contains(targetUrlSubstring) == true
            }
        }
        binding.webView.loadUrl(loginUrl)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(emptyList())
        binding.imageGallery.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
        }
    }

    private fun setupButtonListeners() {
        binding.extractButton.setOnClickListener {
            extractImagesFromWebView()
        }
        binding.pdfButton.setOnClickListener {
            generatePdf()
        }
    }

    private fun setupSeekBarListeners() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyImageFilters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.brightnessSeekbar.setOnSeekBarChangeListener(listener)
        binding.contrastSeekbar.setOnSeekBarChangeListener(listener)
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
                // El resultado es una cadena JSON, la procesamos de forma segura.
                val jsonArray = JSONArray(result)
                val imageUrls = List(jsonArray.length()) { i -> jsonArray.getString(i) }
                runOnUiThread {
                    imageAdapter.updateImages(imageUrls)
                    if (imageUrls.isNotEmpty()) {
                        Toast.makeText(this, "${imageUrls.size} imágenes encontradas.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No se encontraron imágenes en la página.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error al procesar las imágenes de la página.", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun applyImageFilters() {
        val brightness = binding.brightnessSeekbar.progress.toFloat() - 100f
        val contrast = binding.contrastSeekbar.progress.toFloat() / 100f
        imageAdapter.applyFilter(brightness, contrast)
    }

    private fun generatePdf() {
        if (imageAdapter.itemCount == 0) {
            Toast.makeText(this, "No hay imágenes para generar el PDF.", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()

        for (i in 0 until imageAdapter.itemCount) {
            val holder = binding.imageGallery.findViewHolderForAdapterPosition(i) as? ImageAdapter.ImageViewHolder
            holder?.let {
                val view = it.binding.galleryImageView
                // Creamos un bitmap a partir de la vista, que incluye los filtros aplicados.
                val bitmap = view.drawToBitmap()

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
            }
        }

        // Guardamos el documento en la carpeta de Descargas.
        val fileName = "ImageExtractor_${System.currentTimeMillis()}.pdf"
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                    Toast.makeText(this, "PDF guardado en Descargas", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }
}