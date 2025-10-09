package com.example.imageextractor

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.FragmentViewGalleryBinding

class ViewGalleryFragment : Fragment() {

    private enum class ViewMode { DETAIL, ICON }

    private var _binding: FragmentViewGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var detailAdapter: ImageDetailAdapter
    private lateinit var iconAdapter: ImageIconAdapter
    private var imageUrls: List<String> = emptyList()
    private var partidaId: String? = null

    private var currentViewMode = ViewMode.DETAIL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        // Use arguments? to safely access arguments, which might be null
        // when the fragment is first created by the ViewPager.
        arguments?.let {
            imageUrls = it.getStringArray("imageUrls")?.toList() ?: emptyList()
            partidaId = it.getString("partidaId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupAdapters()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = partidaId ?: "Galería"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupAdapters() {
        val onImageClick: (String) -> Unit = { imagePath ->
            val bundle = Bundle().apply {
                putString("imagePath", imagePath)
            }
            findNavController().navigate(R.id.action_viewGalleryFragment_to_imagePreviewFragment, bundle)
        }
        detailAdapter = ImageDetailAdapter(onImageClick)
        iconAdapter = ImageIconAdapter(onImageClick)
    }

    private fun setupRecyclerView() {
        setViewMode(currentViewMode, true)
    }

    private fun setViewMode(mode: ViewMode, isInitialSetup: Boolean = false) {
        if (!isInitialSetup && currentViewMode == mode) return

        currentViewMode = mode
        when (mode) {
            ViewMode.DETAIL -> {
                binding.galleryRecyclerView.layoutManager = LinearLayoutManager(context)
                binding.galleryRecyclerView.adapter = detailAdapter
            }
            ViewMode.ICON -> {
                binding.galleryRecyclerView.layoutManager = GridLayoutManager(context, 3)
                binding.galleryRecyclerView.adapter = iconAdapter
            }
        }
        // Submit list to the new adapter
        detailAdapter.submitList(imageUrls)
        iconAdapter.submitList(imageUrls)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.view_gallery_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_details -> {
                setViewMode(ViewMode.DETAIL)
                true
            }
            R.id.action_view_icons -> {
                setViewMode(ViewMode.ICON)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}