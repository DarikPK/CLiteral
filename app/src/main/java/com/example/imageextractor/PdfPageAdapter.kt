package com.example.imageextractor

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imageextractor.databinding.PdfPageItemBinding
import java.util.Collections

class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private val pages = mutableListOf<Bitmap>()

    class PageViewHolder(private val binding: PdfPageItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bitmap: Bitmap, position: Int) {
            binding.pageImageView.setImageBitmap(bitmap)
            binding.pageNumberText.text = "Página ${position + 1}"
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
        notifyDataSetChanged() // For a full list update, this is sufficient.
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