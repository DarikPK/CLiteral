package com.example.imageextractor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.FolderListItemBinding

class FolderAdapter(
    private val onItemClick: (ImageFolder) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<ImageFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback) {

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false

    inner class FolderViewHolder(
        private val binding: FolderListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(adapterPosition))
                }
            }
        }

        fun bind(folder: ImageFolder) {
            val imageCount = folder.imageFiles.size // Cambiado de imagePaths a imageFiles
            binding.detailsText.text = "${folder.partidaId} - $imageCount ${if (imageCount == 1) "Imagen" else "Imágenes"}"

            binding.checkboxSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = selectedItems.contains(folder.partidaId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setMode(isSelectionMode: Boolean) {
        this.isSelectionMode = isSelectionMode
        if (!isSelectionMode) {
            deselectAll()
        }
        notifyDataSetChanged()
    }

    fun toggleSelection(position: Int) {
        if (position in 0 until itemCount) {
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

    fun selectAll() {
        currentList.forEach { selectedItems.add(it.partidaId) }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<ImageFolder> {
        return currentList.filter { selectedItems.contains(it.partidaId) }
    }

    fun getSelectionSize(): Int = selectedItems.size

    fun selectRange(start: Int, end: Int) {
        val range = if (start < end) (start..end) else (end..start)
        for (i in range) {
            if (i in 0 until itemCount) {
                selectedItems.add(getItem(i).partidaId)
                notifyItemChanged(i)
            }
        }
        onSelectionChanged(selectedItems.size)
    }

    fun deselectRange(start: Int, end: Int) {
        val range = if (start < end) (start..end) else (end..start)
        for (i in range) {
            if (i in 0 until itemCount) {
                selectedItems.remove(getItem(i).partidaId)
                notifyItemChanged(i)
            }
        }
        onSelectionChanged(selectedItems.size)
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
