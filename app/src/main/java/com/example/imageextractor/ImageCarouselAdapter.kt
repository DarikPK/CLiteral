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

        fun bind(imagePath: String, filtersEnabled: Boolean, partidaId: String?, summaryBoxHeaderBitmap: Bitmap?) {
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
            val cutTopPercent = 0.23f
            val cutTop = (extractionBitmap.height * cutTopPercent).toInt()
            val contentHeight = extractionBitmap.height - cutTop
            if (contentHeight <= 0) return extractionBitmap

            val width = extractionBitmap.width
            val scale = width.toFloat() / header.width.toFloat()
            val scaledHeaderHeight = (header.height * scale).toInt()

            val resultHeight = scaledHeaderHeight + contentHeight
            val resultBitmap = Bitmap.createBitmap(width, resultHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            val highQualityPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(header, Rect(0, 0, header.width, header.height), Rect(0, 0, width, scaledHeaderHeight), highQualityPaint)
            canvas.drawBitmap(extractionBitmap, Rect(0, cutTop, width, extractionBitmap.height), Rect(0, scaledHeaderHeight, width, resultHeight), highQualityPaint)

            return resultBitmap
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ImageCarouselItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarouselViewHolder(binding, onZoomStateChanged)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        holder.bind(imageUrls[position], filtersEnabled, partidaId, summaryBoxHeaderBitmap)
    }

    override fun getItemCount(): Int = imageUrls.size
}
