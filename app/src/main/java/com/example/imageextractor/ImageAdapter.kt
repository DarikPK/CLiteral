package com.example.imageextractor

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.GalleryItemBinding

class ImageAdapter(private var imageUrls: List<String>) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private var colorFilter: ColorMatrixColorFilter? = null

    // El ViewHolder contiene la referencia a la vista de cada ítem (el ImageView).
    class ImageViewHolder(val binding: GalleryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = GalleryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        // Usamos Glide para cargar la imagen desde la URL al ImageView.
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .into(holder.binding.galleryImageView)

        // Aplicamos el filtro de color actual si existe.
        holder.binding.galleryImageView.colorFilter = colorFilter
    }

    override fun getItemCount(): Int {
        return imageUrls.size
    }

    // Método para actualizar la lista de imágenes y notificar al RecyclerView.
    fun updateImages(newImageUrls: List<String>) {
        this.imageUrls = newImageUrls
        notifyDataSetChanged()
    }

    // Método para aplicar un filtro de color a todas las imágenes visibles.
    fun applyFilter(brightness: Float, contrast: Float) {
        val matrix = ColorMatrix()
        // El contraste (escala) se aplica primero, luego el brillo (traslación).
        matrix.set(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        this.colorFilter = ColorMatrixColorFilter(matrix)
        // Notificamos que los ítems han cambiado para que se vuelvan a dibujar con el nuevo filtro.
        notifyDataSetChanged()
    }
}