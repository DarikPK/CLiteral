package com.example.imageextractor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.GalleryItemBinding
import java.io.File

class ImageAdapter(private var imageUrls: List<String>) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(val binding: GalleryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = GalleryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]

        // Extraer y establecer el nombre del archivo
        holder.binding.fileNameText.text = File(imageUrl).name

        // Cargar la imagen de la miniatura
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .into(holder.binding.galleryImageView)
    }

    override fun getItemCount() = imageUrls.size

    fun updateImages(newImageUrls: List<String>) {
        this.imageUrls = newImageUrls
        notifyDataSetChanged()
    }
}