package com.example.imageextractor

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.ImageCarouselItemBinding
import java.io.File

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.graphics.Color
import android.content.Context

class ImageCarouselAdapter(
    private val imageUrls: List<String>,
    private val filtersEnabled: Boolean = false,
    private val partidaId: String? = null,
    private val summaryBoxHeaderBitmap: Bitmap? = null,
    private val onZoomStateChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<ImageCarouselAdapter.CarouselViewHolder>() {

    class CarouselViewHolder(
        val binding: ImageCarouselItemBinding,
        private val onZoomStateChanged: (Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String, imageUrls: List<String>, filtersEnabled: Boolean, partidaId: String?, summaryBoxHeaderBitmap: Bitmap?) {
            if (filtersEnabled && partidaId?.startsWith("P", ignoreCase = true) == true &&
                !imagePath.lowercase().contains("_resumen") && summaryBoxHeaderBitmap != null) {

                // Aplicar el filtro de cuadro resumen para la previsualización
                val original = BitmapFactory.decodeFile(imagePath)
                if (original != null) {
                    val filtered = applySummaryBoxFilter(original, summaryBoxHeaderBitmap)
                    binding.zoomableImageView.setImageBitmap(filtered)
                    // Nota: No reciclamos 'filtered' aquí porque la vista lo necesita.
                    // Pero sí el original.
                    original.recycle()
                } else {
                    binding.zoomableImageView.setImageURI(Uri.fromFile(File(imagePath)))
                }
            } else if (filtersEnabled && partidaId?.startsWith("P", ignoreCase = true) == true &&
                imagePath.lowercase().contains("_resumen")) {

                val original = BitmapFactory.decodeFile(imagePath)
                if (original != null) {
                    val firstSummaryPath = imageUrls.find { it.lowercase().contains("_resumen") }
                    val patched = applySummaryPatch(original, imagePath == firstSummaryPath)
                    binding.zoomableImageView.setImageBitmap(patched)
                    original.recycle()
                } else {
                    binding.zoomableImageView.setImageURI(Uri.fromFile(File(imagePath)))
                }
            } else {
                binding.zoomableImageView.setImageURI(Uri.fromFile(File(imagePath)))
            }

            if (filtersEnabled) {
                val brightness = (35f - 50f) * 5f
                val contrast = 95f / 50f
                val cm = ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                ))
                binding.zoomableImageView.colorFilter = ColorMatrixColorFilter(cm)
            } else {
                binding.zoomableImageView.clearColorFilter()
            }
            binding.zoomableImageView.setOnMatrixChangedListener(object : ZoomableImageView.OnMatrixChangedListener {
                override fun onMatrixChanged() {
                    onZoomStateChanged(binding.zoomableImageView.isZoomed())
                }
            })
        }

        private fun applySummaryBoxFilter(extractionBitmap: Bitmap, header: Bitmap): Bitmap {
            val width = extractionBitmap.width
            val originalHeight = extractionBitmap.height
            val sharedPrefs = itemView.context.getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
            val ptToPx = width / 595f

            // --- CONFIGURACIÓN DE PARCHES ---
            // Parche Superior (En el encabezado)
            val pTopX = sharedPrefs.getFloat("gallery_filter_patch_top_offset_x", 0f) * ptToPx
            val pTopY = sharedPrefs.getFloat("gallery_filter_patch_top_offset_y", 0f) * ptToPx
            val pTopW = sharedPrefs.getFloat("gallery_filter_patch_top_width_percent", 32f) / 100f
            val pTopH = sharedPrefs.getFloat("gallery_filter_patch_top_height_percent", 18f) / 100f

            // Parche Inferior (Pie de página)
            val pBotX = sharedPrefs.getFloat("gallery_filter_patch_bottom_offset_x", 0f) * ptToPx
            val pBotY = sharedPrefs.getFloat("gallery_filter_patch_bottom_offset_y", 0f) * ptToPx
            val pBotW = sharedPrefs.getFloat("gallery_filter_patch_bottom_width_percent", 100f) / 100f
            val pBotH = sharedPrefs.getFloat("gallery_filter_patch_bottom_height_percent", 6.5f) / 100f

            // Parche Lateral (Derecha)
            val pSideX = sharedPrefs.getFloat("gallery_filter_patch_side_offset_x", 0f) * ptToPx
            val pSideY = sharedPrefs.getFloat("gallery_filter_patch_side_offset_y", 0f) * ptToPx
            val pSideW = sharedPrefs.getFloat("gallery_filter_patch_side_width_percent", 4.7f) / 100f
            val pSideH = sharedPrefs.getFloat("gallery_filter_patch_side_height_percent", 24f) / 100f

            // --- CONFIGURACIÓN DE TEXTO ---
            val filterText = sharedPrefs.getString("gallery_filter_text_content", "CERTIFICADO LITERAL") ?: "CERTIFICADO LITERAL"
            val filterTextSizePercent = sharedPrefs.getFloat("gallery_filter_text_width_percent", 11f) / 100f
            val filterColorStr = sharedPrefs.getString("gallery_filter_text_color", "#000000") ?: "#000000"
            val filterLineSpacing = sharedPrefs.getFloat("gallery_filter_text_line_spacing", 1.0f)
            val filterOffsetX = sharedPrefs.getFloat("gallery_filter_text_offset_x", 0f) * ptToPx
            val filterOffsetY = sharedPrefs.getFloat("gallery_filter_text_offset_y", 0f) * ptToPx

            // 1. Altura escalada del nuevo cuadro de resumen
            val scale = width.toFloat() / header.width.toFloat()
            val scaledHeaderHeight = (header.height * scale).toInt()

            // 2. Punto de corte en la hoja de extracción original (14% para conservar asientos)
            val cutTop = (originalHeight * 0.14f).toInt()

            // 3. Punto de destino: Justo después del nuevo cuadro, sin huecos en blanco.
            val destinationTop = scaledHeaderHeight

            // 4. Crear bitmap con las dimensiones originales
            val resultBitmap = Bitmap.createBitmap(width, originalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            val highQualityPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // 5. Dibujar el nuevo encabezado (cuadro resumen)
            val headerSrc = Rect(0, 0, header.width, header.height)
            val headerDst = Rect(0, 0, width, scaledHeaderHeight)
            canvas.drawBitmap(header, headerSrc, headerDst, highQualityPaint)

            // DIBUJAR PARCHE SUPERIOR
            val patchPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            val rectW = width * pTopW
            val rectH = scaledHeaderHeight * pTopH
            val rectL = (width - rectW) / 2f + pTopX
            val rectT = (scaledHeaderHeight * 0.33f) + pTopY
            canvas.drawRect(rectL, rectT, rectL + rectW, rectT + rectH, patchPaint)

            // DIBUJAR TEXTO
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                try {
                    color = Color.parseColor(filterColorStr)
                } catch (e: Exception) {
                    color = Color.BLACK
                }
                textSize = scaledHeaderHeight * filterTextSizePercent
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            val staticLayout = StaticLayout.Builder.obtain(filterText, 0, filterText.length, textPaint, rectW.toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, filterLineSpacing)
                .build()

            canvas.save()
            val drawX = rectL + filterOffsetX
            val drawY = rectT + rectH / 2f + filterOffsetY - staticLayout.height / 2f
            canvas.translate(drawX, drawY)
            staticLayout.draw(canvas)
            canvas.restore()

            // 6. Dibujar el contenido de la extracción DESPLAZADO hacia abajo.
            // Se coloca exactamente debajo del nuevo encabezado.
            // Lo que sobre al final del alto original se recorta (clip) automáticamente.
            val contentSrc = Rect(0, cutTop, width, cutTop + (originalHeight - destinationTop))
            val contentDst = Rect(0, destinationTop, width, originalHeight)

            if (contentDst.height() > 0) {
                canvas.drawBitmap(extractionBitmap, contentSrc, contentDst, highQualityPaint)
            }

            // 7. Dibujar parche blanco para eliminar texto vertical a la derecha
            patchPaint.color = android.graphics.Color.WHITE
            patchPaint.style = Paint.Style.FILL

            val pSideLeft = (width * 0.908f) + pSideX
            val pSideTop = (originalHeight * 0.18f) + pSideY
            val pSideRight = pSideLeft + (width * pSideW)
            val pSideBottom = pSideTop + (originalHeight * pSideH)

            canvas.drawRect(pSideLeft, pSideTop, pSideRight, pSideBottom, patchPaint)

            return resultBitmap
        }
        private fun applySummaryPatch(bitmap: Bitmap, showTitle: Boolean): Bitmap {
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(bitmap, 0f, 0f, null)

            val sharedPrefs = itemView.context.getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
            val ptToPx = bitmap.width / 595f

            // --- CONFIGURACIÓN DE PARCHES ---
            val pTopX = sharedPrefs.getFloat("gallery_filter_patch_top_offset_x", 0f) * ptToPx
            val pTopY = sharedPrefs.getFloat("gallery_filter_patch_top_offset_y", 0f) * ptToPx
            val pTopW = sharedPrefs.getFloat("gallery_filter_patch_top_width_percent", 32f) / 100f
            val pTopH = sharedPrefs.getFloat("gallery_filter_patch_top_height_percent", 18f) / 100f

            val pBotX = sharedPrefs.getFloat("gallery_filter_patch_bottom_offset_x", 0f) * ptToPx
            val pBotY = sharedPrefs.getFloat("gallery_filter_patch_bottom_offset_y", 0f) * ptToPx
            val pBotW = sharedPrefs.getFloat("gallery_filter_patch_bottom_width_percent", 100f) / 100f
            val pBotH = sharedPrefs.getFloat("gallery_filter_patch_bottom_height_percent", 6.5f) / 100f

            // --- CONFIGURACIÓN DE TEXTO ---
            val filterText = sharedPrefs.getString("gallery_filter_text_content", "CERTIFICADO LITERAL") ?: "CERTIFICADO LITERAL"
            val filterTextSizePercent = sharedPrefs.getFloat("gallery_filter_text_width_percent", 11f) / 100f
            val filterColorStr = sharedPrefs.getString("gallery_filter_text_color", "#000000") ?: "#000000"
            val filterLineSpacing = sharedPrefs.getFloat("gallery_filter_text_line_spacing", 1.0f)
            val filterOffsetX = sharedPrefs.getFloat("gallery_filter_text_offset_x", 0f) * ptToPx
            val filterOffsetY = sharedPrefs.getFloat("gallery_filter_text_offset_y", 0f) * ptToPx

            val paint = Paint()
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.FILL

            // Parche inferior (pie de página) - Siempre visible
            val pBotL = pBotX
            val pBotT = (bitmap.height * 0.935f) + pBotY
            val pBotR = pBotL + (bitmap.width * pBotW)
            val pBotB = pBotT + (bitmap.height * pBotH)
            canvas.drawRect(pBotL, pBotT, pBotR, pBotB, paint)

            if (showTitle) {
                // Parche superior para el título - Solo visible si showTitle es true
                // (Primera hoja de resumen o cualquier hoja de extracción)
                val headerHeight = bitmap.height * 0.16f
                val rectW = bitmap.width * pTopW
                val rectH = headerHeight * pTopH
                val rectL = (bitmap.width - rectW) / 2f + pTopX
                val rectT = (headerHeight * 0.33f) + pTopY
                canvas.drawRect(rectL, rectT, rectL + rectW, rectT + rectH, paint)

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    try {
                        color = Color.parseColor(filterColorStr)
                    } catch (e: Exception) {
                        color = Color.BLACK
                    }
                    textSize = headerHeight * filterTextSizePercent
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }

                val staticLayout = StaticLayout.Builder.obtain(filterText, 0, filterText.length, textPaint, rectW.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, filterLineSpacing)
                    .build()

                canvas.save()
                val drawX = rectL + filterOffsetX
                val drawY = rectT + rectH / 2f + filterOffsetY - staticLayout.height / 2f

                canvas.translate(drawX, drawY)
                staticLayout.draw(canvas)
                canvas.restore()
            }

            return result
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ImageCarouselItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarouselViewHolder(binding, onZoomStateChanged)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        holder.bind(imageUrls[position], imageUrls, filtersEnabled, partidaId, summaryBoxHeaderBitmap)
    }

    override fun getItemCount(): Int = imageUrls.size
}
