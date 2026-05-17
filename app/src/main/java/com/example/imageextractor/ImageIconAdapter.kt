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

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

class ImageIconAdapter(
    private val onImageClick: (String) -> Unit,
    private val onImageLongClick: (String) -> Unit
) : ListAdapter<String, ImageIconAdapter.IconViewHolder>(DiffUtilCallback()) {

    private var filtersEnabled = true

    fun setFiltersEnabled(enabled: Boolean) {
        filtersEnabled = enabled
        notifyDataSetChanged()
    }

    class IconViewHolder(
        private val binding: GalleryItemBinding,
        private val onImageClick: (String) -> Unit,
        private val onImageLongClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String, filtersEnabled: Boolean) {
            val file = File(imagePath)
            if (!file.exists()) return

            binding.fileNameText.text = file.name

            Glide.with(binding.root.context)
                .load(Uri.fromFile(file))
                .centerCrop()
                .into(binding.galleryImageView)

            if (filtersEnabled) {
                val brightness = (35f - 50f) * 5f
                val contrast = 95f / 50f
                val cm = ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                ))
                binding.galleryImageView.colorFilter = ColorMatrixColorFilter(cm)
            } else {
                binding.galleryImageView.clearColorFilter()
            }

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
        holder.bind(getItem(position), filtersEnabled)
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