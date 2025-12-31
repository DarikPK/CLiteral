package com.example.imageextractor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.imageextractor.databinding.FolderItemBinding
import com.example.imageextractor.databinding.FolderListItemBinding

class FolderAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<ImageFolder, FolderAdapter.BaseViewHolder>(FolderDiffCallback) {

    private val selectedItems = mutableSetOf<String>()
    private var currentViewType = ViewType.GRID

    enum class ViewType {
        GRID, LIST
    }

    abstract class BaseViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(folder: ImageFolder, isSelected: Boolean)
    }

    inner class GridViewHolder(
        private val binding: FolderItemBinding
    ) : BaseViewHolder(binding) {
        init {
            itemView.setOnClickListener { toggleSelection(adapterPosition) }
            binding.checkboxSelect.setOnClickListener { toggleSelection(adapterPosition) }
        }

        override fun bind(folder: ImageFolder, isSelected: Boolean) {
            binding.partidaIdText.text = folder.partidaId
            val imageCount = folder.imagePaths.size
            binding.imageCountText.text = "$imageCount ${if (imageCount == 1) "imagen" else "imágenes"}"
            binding.checkboxSelect.isChecked = isSelected
        }
    }

    inner class ListViewHolder(
        private val binding: FolderListItemBinding
    ) : BaseViewHolder(binding) {
        init {
            itemView.setOnClickListener { toggleSelection(adapterPosition) }
            binding.checkboxSelect.setOnClickListener { toggleSelection(adapterPosition) }
        }

        override fun bind(folder: ImageFolder, isSelected: Boolean) {
            val imageCount = folder.imagePaths.size
            binding.detailsText.text = "Nro de partida ${folder.partidaId} - $imageCount ${if (imageCount == 1) "Imagen" else "Imágenes"}"
            binding.checkboxSelect.isChecked = isSelected
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.LIST.ordinal -> {
                val binding = FolderListItemBinding.inflate(inflater, parent, false)
                ListViewHolder(binding)
            }
            else -> {
                val binding = FolderItemBinding.inflate(inflater, parent, false)
                GridViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val folder = getItem(position)
        holder.bind(folder, selectedItems.contains(folder.partidaId))
    }

    override fun getItemViewType(position: Int): Int {
        return currentViewType.ordinal
    }

    fun setViewType(viewType: ViewType) {
        if (currentViewType != viewType) {
            currentViewType = viewType
            notifyDataSetChanged()
        }
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
