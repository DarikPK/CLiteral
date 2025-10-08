package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imageextractor.databinding.FragmentViewGalleryBinding

class ViewGalleryFragment : Fragment() {

    private var _binding: FragmentViewGalleryBinding? = null
    private val binding get() = _binding!!

    private val args: ViewGalleryFragmentArgs by navArgs()
    private lateinit var imageAdapter: ImageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        displayImages()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(emptyList())
        binding.galleryRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 3) // 3 columnas
            adapter = imageAdapter
        }
    }

    private fun displayImages() {
        val imageUrls = args.imageUrls.toList()
        imageAdapter.updateImages(imageUrls)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}