package com.example.imageextractor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.FolderItemBinding

class FolderAdapter(
    private val onItemClick: (ImageFolder) -> Unit,
    private val onItemLongClick: (ImageFolder) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<ImageFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback) {

    private var isSelectionModeActive = false
    private val selectedItems = mutableSetOf<String>()

    inner class FolderViewHolder(
        private val binding: FolderItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(adapterPosition))
                }
            }
            itemView.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(adapterPosition))
                }
                true
            }
        }

        fun bind(folder: ImageFolder) {
            binding.partidaIdText.text = folder.partidaId
            val imageCount = folder.imagePaths.size
            binding.imageCountText.text = "$imageCount ${if (imageCount == 1) "imagen" else "imágenes"}"

            binding.checkboxSelect.visibility = if (isSelectionModeActive) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = selectedItems.contains(folder.partidaId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectionMode(isActive: Boolean) {
        isSelectionModeActive = isActive
        if (!isActive) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
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
}

object FolderDiffCallback : DiffUtil.ItemCallback<ImageFolder>() {
    override fun areItemsTheSame(oldItem: ImageFolder, newItem: ImageFolder): Boolean {
        return oldItem.partidaId == newItem.partidaId
    }

    override fun areContentsTheSame(oldItem: ImageFolder, newItem: ImageFolder): Boolean {
        return oldItem == newItem
    }
}
