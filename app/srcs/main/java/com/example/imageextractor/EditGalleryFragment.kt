package com.example.imageextractor

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.FragmentEditGalleryBinding
import com.google.android.material.tabs.TabLayout
import java.io.File

class EditGalleryFragment : Fragment() {

    private var _binding: FragmentEditGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderAdapter: FolderAdapter
    private var deleteMenuItem: MenuItem? = null
    private var selectAllMenuItem: MenuItem? = null
    private var isSelectionMode = false

    private val storagePermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.READ_EXTERNAL_STORAGE else Manifest.permission.WRITE_EXTERNAL_STORAGE

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) loadFoldersFromStorage() else Toast.makeText(requireContext(), "Permiso denegado.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Partidas Capturadas"

        setupRecyclerView()
        setupTabLayout()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermission()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onItemClick = { folder ->
                if (!isSelectionMode) {
                    val bundle = Bundle().apply {
                        putStringArray("imageUrls", folder.imagePaths.toTypedArray())
                        putString("partidaId", folder.partidaId)
                    }
                    findNavController().navigate(R.id.action_editingFragment_to_viewGalleryFragment, bundle)
                }
            },
            onSelectionChanged = { selectionSize ->
                updateMenuState(selectionSize)
            }
        )

        val dragSelectListener = DragSelectTouchListener(binding.editGalleryRecyclerView, folderAdapter) { isSelectionMode }

        binding.editGalleryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
            addOnItemTouchListener(dragSelectListener)
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> setMode(false) // Modo Visualización
                    1 -> setMode(true)  // Modo Selección
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setMode(isSelection: Boolean) {
        isSelectionMode = isSelection
        folderAdapter.setMode(isSelection)
        activity?.invalidateOptionsMenu()
    }

    private fun updateMenuState(selectionSize: Int) {
        if (!isSelectionMode) return
        deleteMenuItem?.isVisible = selectionSize > 0
        val totalItems = folderAdapter.itemCount
        selectAllMenuItem?.title = if (totalItems > 0 && selectionSize == totalItems) "Deseleccionar Todo" else "Seleccionar Todo"
    }

    private fun showDeleteConfirmationDialog() {
        val selected = folderAdapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Deseas eliminar ${selected.size} partida(s)? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                var totalDeletedFiles = 0
                selected.forEach { totalDeletedFiles += deleteFolderContents(it) }

                if (totalDeletedFiles > 0) {
                    Toast.makeText(context, "$totalDeletedFiles archivo(s) eliminado(s).", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No se pudieron eliminar los archivos. Comprueba los permisos.", Toast.LENGTH_LONG).show()
                }

                folderAdapter.deselectAll()
                loadFoldersFromStorage()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteFolderContents(folder: ImageFolder) {
        folder.imagePaths.forEach { File(it).delete() }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), storagePermission) == PackageManager.PERMISSION_GRANTED -> loadFoldersFromStorage()
            else -> requestPermissionLauncher.launch(storagePermission)
        }
    }

    private fun loadFoldersFromStorage() {
        val folders = mutableMapOf<String, MutableList<String>>()
        val imageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "capturas_sunarp")

        if (imageDir.exists() && imageDir.isDirectory) {
            imageDir.listFiles { file ->
                file.isFile && file.name.endsWith(".png") && file.name.contains("-Hoja ")
            }?.forEach { file ->
                val partidaId = file.name.substringBefore("-Hoja").trim()
                if (partidaId.isNotEmpty()) {
                    folders.getOrPut(partidaId) { mutableListOf() }.add(file.absolutePath)
                }
            }
        }

        val folderList = folders.map { (partidaId, paths) ->
            val sortedPaths = paths.sortedBy { it.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0 }
            ImageFolder(partidaId = partidaId, imagePaths = sortedPaths)
        }

        folderAdapter.submitList(folderList)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.edit_gallery_menu, menu)
        deleteMenuItem = menu.findItem(R.id.action_delete_selection)
        selectAllMenuItem = menu.findItem(R.id.action_select_all)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        deleteMenuItem?.isVisible = isSelectionMode && folderAdapter.getSelectionSize() > 0
        selectAllMenuItem?.isVisible = isSelectionMode
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_selection -> {
                showDeleteConfirmationDialog()
                true
            }
            R.id.action_select_all -> {
                if (folderAdapter.getSelectionSize() == folderAdapter.itemCount) {
                    folderAdapter.deselectAll()
                } else {
                    folderAdapter.selectAll()
                }
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
