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
    private val onClick: (ImageFolder) -> Unit,
    private val onDelete: (ImageFolder) -> Unit
) : ListAdapter<ImageFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback) {

    private var currentViewType = ViewType.GRID

    enum class ViewType {
        GRID, LIST
    }

    abstract class FolderViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(folder: ImageFolder)
    }

    inner class GridViewHolder(
        private val binding: FolderItemBinding
    ) : FolderViewHolder(binding) {
        override fun bind(folder: ImageFolder) {
            binding.partidaIdText.text = folder.partidaId
            val imageCount = folder.imagePaths.size
            binding.imageCountText.text = "$imageCount ${if (imageCount == 1) "imagen" else "imágenes"}"
            itemView.setOnClickListener { onClick(folder) }
            itemView.setOnLongClickListener {
                onDelete(folder)
                true
            }
        }
    }

    inner class ListViewHolder(
        private val binding: FolderListItemBinding
    ) : FolderViewHolder(binding) {
        override fun bind(folder: ImageFolder) {
            binding.partidaIdText.text = folder.partidaId
            val imageCount = folder.imagePaths.size
            binding.imageCountText.text = "$imageCount ${if (imageCount == 1) "imagen" else "imágenes"}"
            itemView.setOnClickListener { onClick(folder) }
            binding.deleteButton.setOnClickListener { onDelete(folder) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
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

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.bind(folder)
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
}

object FolderDiffCallback : DiffUtil.ItemCallback<ImageFolder>() {
    override fun areItemsTheSame(oldItem: ImageFolder, newItem: ImageFolder): Boolean {
        return oldItem.partidaId == newItem.partidaId
    }

    override fun areContentsTheSame(oldItem: ImageFolder, newItem: ImageFolder): Boolean {
        return oldItem == newItem
    }
}
