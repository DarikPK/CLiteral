package com.example.imageextractor

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.ImageCarouselItemBinding
import java.io.File

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

class ImageCarouselAdapter(
    private val imageUrls: List<String>,
    private val filtersEnabled: Boolean = false,
    private val onZoomStateChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<ImageCarouselAdapter.CarouselViewHolder>() {

    class CarouselViewHolder(
        val binding: ImageCarouselItemBinding,
        private val onZoomStateChanged: (Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String, filtersEnabled: Boolean) {
            binding.zoomableImageView.setImageURI(Uri.fromFile(File(imagePath)))

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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ImageCarouselItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarouselViewHolder(binding, onZoomStateChanged)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        holder.bind(imageUrls[position], filtersEnabled)
    }

    override fun getItemCount(): Int = imageUrls.size
}
