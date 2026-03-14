package com.example.imageextractor

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.GalleryItemBinding
import java.io.File

class ImageIconAdapter(
    private val onImageClick: (String) -> Unit,
    private val onImageLongClick: (String) -> Unit
) : ListAdapter<String, ImageIconAdapter.IconViewHolder>(DiffUtilCallback()) {

    class IconViewHolder(
        private val binding: GalleryItemBinding,
        private val onImageClick: (String) -> Unit,
        private val onImageLongClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String) {
            val file = File(imagePath)
            if (!file.exists()) return

            binding.fileNameText.text = file.name

            Glide.with(binding.root.context)
                .load(Uri.fromFile(file))
                .centerCrop()
                .into(binding.galleryImageView)

            itemView.setOnClickListener {
                onImageClick(imagePath)
            }

            itemView.setOnLongClickListener {
                onImageLongClick(imagePath)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val binding = GalleryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IconViewHolder(binding, onImageClick, onImageLongClick)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffUtilCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}