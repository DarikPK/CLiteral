package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imageextractor.databinding.FragmentViewGalleryBinding

class ViewGalleryFragment : Fragment() {

    private var _binding: FragmentViewGalleryBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
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
        observeViewModel()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(emptyList())
        binding.galleryRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 3) // 3 columnas
            adapter = imageAdapter
        }
    }

    private fun observeViewModel() {
        sharedViewModel.imageUrls.observe(viewLifecycleOwner) { urls ->
            urls?.let { imageAdapter.updateImages(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}