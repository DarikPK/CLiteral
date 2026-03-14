package com.example.imageextractor

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.ImageDetailItemBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageDetailAdapter(
    private val onImageClick: (String) -> Unit,
    private val onImageLongClick: (String) -> Unit
) : ListAdapter<String, ImageDetailAdapter.DetailViewHolder>(DiffUtilCallback()) {

    class DetailViewHolder(
        private val binding: ImageDetailItemBinding,
        private val onImageClick: (String) -> Unit,
        private val onImageLongClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String) {
            val file = File(imagePath)
            if (!file.exists()) return

            binding.fileNameText.text = file.name

            // Load thumbnail using Glide
            Glide.with(binding.root.context)
                .load(Uri.fromFile(file))
                .centerCrop()
                .into(binding.thumbnailImageView)

            // Format date
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.fileDateText.text = sdf.format(Date(file.lastModified()))

            // Format size
            val sizeInMb = file.length() / (1024.0 * 1024.0)
            binding.fileSizeText.text = String.format(Locale.getDefault(), "%.2f MB", sizeInMb)

            itemView.setOnClickListener {
                onImageClick(imagePath)
            }

            itemView.setOnLongClickListener {
                onImageLongClick(imagePath)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val binding = ImageDetailItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DetailViewHolder(binding, onImageClick, onImageLongClick)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
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