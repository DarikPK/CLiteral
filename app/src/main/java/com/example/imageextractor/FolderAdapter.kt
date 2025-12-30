package com.example.imageextractor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.FolderListItemBinding

class FolderAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<ImageFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback) {

    private val selectedItems = mutableSetOf<String>()

    inner class FolderViewHolder(
        private val binding: FolderListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                toggleSelection(adapterPosition)
            }
            binding.checkboxSelect.setOnClickListener {
                toggleSelection(adapterPosition)
            }
        }

        fun bind(folder: ImageFolder) {
            binding.partidaIdText.text = folder.partidaId
            val imageCount = folder.imagePaths.size
            binding.imageCountText.text = "$imageCount ${if (imageCount == 1) "imagen" else "imágenes"}"
            binding.checkboxSelect.isChecked = selectedItems.contains(folder.partidaId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.bind(folder)
    }

    private fun toggleSelection(position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            val folder = getItem(position)
            if (selectedItems.contains(folder.partidaId)) {
                selectedItems.remove(folder.partidaId)
            } else {
                selectedItems.add(folder.partidaId)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedItems.size)
        }
    }

    fun getSelectedItems(): List<ImageFolder> {
        return currentList.filter { selectedItems.contains(it.partidaId) }
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
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
