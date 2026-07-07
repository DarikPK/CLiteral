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

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", android.content.Context.MODE_PRIVATE)
    }

    private lateinit var detailAdapter: ImageDetailAdapter
    private lateinit var iconAdapter: ImageIconAdapter
    private var imageUrls: MutableList<String> = mutableListOf()
    private var partidaId: String? = null
    private var tipoPartida: String? = null

    private var currentViewMode = ViewMode.ICON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        // Use arguments? to safely access arguments, which might be null
        // when the fragment is first created by the ViewPager.
        arguments?.let {
            imageUrls = it.getStringArray("imageUrls")?.toMutableList() ?: mutableListOf()
            partidaId = it.getString("partidaId")
            tipoPartida = it.getString("tipoPartida")
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

        // Mostrar barra de filtros solo si es Partida P y tipo PREDIOS
        val isPrediosP = (partidaId?.startsWith("P", ignoreCase = true) == true) &&
                         (tipoPartida == "PREDIOS")

        binding.filterBarContainer.visibility = if (isPrediosP) View.VISIBLE else View.GONE

        binding.switchFilters.isChecked = sharedPrefs.getBoolean("gallery_filters_enabled", true)
        detailAdapter.setFiltersEnabled(binding.switchFilters.isChecked)
        iconAdapter.setFiltersEnabled(binding.switchFilters.isChecked)

        binding.switchFilters.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("gallery_filters_enabled", isChecked).apply()
            detailAdapter.setFiltersEnabled(isChecked)
            iconAdapter.setFiltersEnabled(isChecked)
        }

        binding.btnFilterConfig.setOnClickListener {
            GalleryFilterConfigDialogFragment().show(parentFragmentManager, "GalleryFilterConfig")
        }
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
                putStringArray("imageUrls", imageUrls.toTypedArray())
                putInt("initialIndex", imageUrls.indexOf(imagePath))
                putBoolean("filtersEnabled", binding.switchFilters.isChecked)
                putString("partidaId", partidaId)
            }
            findNavController().navigate(R.id.action_viewGalleryFragment_to_imagePreviewFragment, bundle)
        }

        val onImageLongClick: (String) -> Unit = { imagePath ->
            showDeleteConfirmation(imagePath)
        }

        detailAdapter = ImageDetailAdapter(onImageClick, onImageLongClick)
        iconAdapter = ImageIconAdapter(onImageClick, onImageLongClick)
    }

    private fun showDeleteConfirmation(imagePath: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Imagen")
            .setMessage("¿Estás seguro de que deseas eliminar esta imagen?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteImage(imagePath)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteImage(imagePath: String) {
        val file = java.io.File(imagePath)
        if (file.exists() && file.delete()) {
            imageUrls.remove(imagePath)
            detailAdapter.submitList(ArrayList(imageUrls))
            iconAdapter.submitList(ArrayList(imageUrls))
            android.widget.Toast.makeText(context, "Imagen eliminada", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "No se pudo eliminar la imagen", android.widget.Toast.LENGTH_SHORT).show()
        }
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