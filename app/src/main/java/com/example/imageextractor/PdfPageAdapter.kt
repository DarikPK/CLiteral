package com.example.imageextractor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.PdfPageItemBinding
import java.util.Collections

class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private val pages = mutableListOf<Bitmap>()

    class PageViewHolder(private val binding: PdfPageItemBinding) :
        RecyclerView.ViewHolder(binding.root), ZoomableImageView.OnMatrixChangeListener {

        init {
            binding.pageImageView.setOnMatrixChangeListener(this)
        }

        fun bind(bitmap: Bitmap, position: Int) {
            binding.pageImageView.setImageBitmap(bitmap)
            binding.pageNumberText.text = "Página ${position + 1}"

            // For demonstration, we'll make the stamp visible.
            // In a real app, this would be driven by settings from a ViewModel.
            binding.stampImageView.visibility = View.VISIBLE
            binding.stampImageView.setImageResource(android.R.drawable.ic_menu_myplaces) // Placeholder stamp
        }

        override fun onMatrixChanged(matrix: Matrix) {
            binding.stampImageView.imageMatrix = matrix
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = PdfPageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }

    override fun getItemCount(): Int = pages.size

    fun submitList(newPages: List<Bitmap>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(pages, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(pages, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentList(): List<Bitmap> {
        return pages
    }
}
