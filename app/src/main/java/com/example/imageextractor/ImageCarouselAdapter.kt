package com.example.imageextractor

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.ImageCarouselItemBinding
import java.io.File

class ImageCarouselAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ImageCarouselAdapter.CarouselViewHolder>() {

    class CarouselViewHolder(private val binding: ImageCarouselItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String) {
            binding.zoomableImageView.setImageURI(Uri.fromFile(File(imagePath)))
            binding.fabZoomIn.setOnClickListener { binding.zoomableImageView.zoomIn() }
            binding.fabZoomReset.setOnClickListener { binding.zoomableImageView.resetZoom() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ImageCarouselItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarouselViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        holder.bind(imageUrls[position])
    }

    override fun getItemCount(): Int = imageUrls.size
}
