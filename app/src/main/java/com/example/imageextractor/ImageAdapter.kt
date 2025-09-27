package com.example.imageextractor

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.GalleryItemBinding

class ImageAdapter(private var imageUrls: List<String>) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private var colorFilter: ColorMatrixColorFilter? = null

    class ImageViewHolder(val binding: GalleryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = GalleryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .into(holder.binding.galleryImageView)

        holder.binding.galleryImageView.colorFilter = colorFilter
    }

    override fun getItemCount() = imageUrls.size

    fun updateImages(newImageUrls: List<String>) {
        this.imageUrls = newImageUrls
        notifyDataSetChanged()
    }

    fun applyFilter(brightness: Float, contrast: Float) {
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        this.colorFilter = ColorMatrixColorFilter(matrix)
        notifyDataSetChanged()
    }

    // Devuelve la lista actual de URLs.
    fun getCurrentImageUrls(): List<String> {
        return imageUrls
    }
}