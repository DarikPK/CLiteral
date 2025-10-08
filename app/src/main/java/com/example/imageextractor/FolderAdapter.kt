package com.example.imageextractor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.FolderItemBinding

class FolderAdapter(private val onClick: (ImageFolder) -> Unit) :
    ListAdapter<ImageFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback) {

    class FolderViewHolder(private val binding: FolderItemBinding, val onClick: (ImageFolder) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentFolder: ImageFolder? = null

        init {
            itemView.setOnClickListener {
                currentFolder?.let {
                    onClick(it)
                }
            }
        }

        fun bind(folder: ImageFolder) {
            currentFolder = folder
            binding.partidaIdText.text = "Partida: ${folder.partidaId}"
            val imageCount = folder.imagePaths.size
            binding.imageCountText.text = "$imageCount ${if (imageCount == 1) "imagen" else "imágenes"}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.bind(folder)
    }
}

object FolderDiffCallback : DiffUtil.ItemCallback<ImageFolder>() {
    override fun areItemsTheSame(oldItem: ImageFolder, newItem: ImageFolder): Boolean {
        return oldItem.partidaId == newItem.partidaId
    }

    override fun areContentsTheSame(oldItem: ImageFolder, newItem: ImageFolder): Boolean {
        return oldItem == newItem
    }
}