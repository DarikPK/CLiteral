package com.example.imageextractor

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imageextractor.databinding.FragmentEditGalleryBinding
import java.io.File

class EditGalleryFragment : Fragment() {

    private enum class Mode { VIEW, SELECTION }

    private var _binding: FragmentEditGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderAdapter: FolderAdapter
    private var currentMode: Mode = Mode.VIEW
    private var actionMode: ActionMode? = null

    private val storagePermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            loadFoldersFromStorage()
        } else {
            Toast.makeText(requireContext(), "El permiso para leer archivos es necesario para esta función.", Toast.LENGTH_LONG).show()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.contextual_selection_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val selectionSize = folderAdapter.getSelectionSize()
            val selectAllItem = menu.findItem(R.id.action_select_all)
            selectAllItem.title = if (selectionSize == folderAdapter.itemCount) "Deseleccionar Todo" else "Seleccionar Todo"
            mode.title = "$selectionSize seleccionado(s)"
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete_selection -> {
                    showDeleteConfirmationDialog(folderAdapter.getSelectedItems())
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
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            exitSelectionMode()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermission()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onItemClick = { folder ->
                if (currentMode == Mode.SELECTION) {
                    val position = folderAdapter.currentList.indexOf(folder)
                    folderAdapter.toggleSelection(position)
                } else {
                    val bundle = Bundle().apply {
                        putStringArray("imageUrls", folder.imagePaths.toTypedArray())
                        putString("partidaId", folder.partidaId)
                    }
                    findNavController().navigate(R.id.action_editingFragment_to_viewGalleryFragment, bundle)
                }
            },
            onItemLongClick = { folder ->
                if (currentMode != Mode.SELECTION) {
                    enterSelectionMode()
                    val position = folderAdapter.currentList.indexOf(folder)
                    folderAdapter.toggleSelection(position)
                }
            },
            onSelectionChanged = { selectionSize ->
                if (currentMode == Mode.SELECTION) {
                    if (selectionSize == 0) {
                        actionMode?.finish()
                    } else {
                        actionMode?.invalidate()
                    }
                }
            }
        )

        val dragListener = DragSelectTouchListener(binding.editGalleryRecyclerView, folderAdapter,
            isInSelectionMode = { currentMode == Mode.SELECTION },
            onDragSelectionFinished = { actionMode?.invalidate() }
        )

        binding.editGalleryRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = folderAdapter
            addOnItemTouchListener(dragListener)
        }
    }

    private fun enterSelectionMode() {
        currentMode = Mode.SELECTION
        actionMode = (activity as? AppCompatActivity)?.startActionMode(actionModeCallback)
        folderAdapter.setSelectionMode(true)
    }

    private fun exitSelectionMode() {
        currentMode = Mode.VIEW
        actionMode = null
        folderAdapter.setSelectionMode(false)
    }

    private fun showDeleteConfirmationDialog(foldersToDelete: List<ImageFolder>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Deseas eliminar ${foldersToDelete.size} partida(s)? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                foldersToDelete.forEach { deleteFolderContents(it) }
                actionMode?.finish()
                loadFoldersFromStorage()
                Toast.makeText(context, "${foldersToDelete.size} partida(s) eliminada(s).", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteFolderContents(folder: ImageFolder) {
        folder.imagePaths.forEach { File(it).delete() }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), storagePermission) == PackageManager.PERMISSION_GRANTED -> {
                loadFoldersFromStorage()
            }
            shouldShowRequestPermissionRationale(storagePermission) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Permiso Necesario")
                    .setMessage("Para mostrar las extracciones guardadas, la aplicación necesita permiso para leer los archivos.")
                    .setPositiveButton("Entendido") { _, _ -> requestPermissionLauncher.launch(storagePermission) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(storagePermission)
            }
        }
    }

    private fun loadFoldersFromStorage() {
        val folders = mutableMapOf<String, MutableList<String>>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val imageDir = File(downloadsDir, "capturas_sunarp")

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

        if (folderList.isEmpty()) {
            Toast.makeText(context, "No se encontraron extracciones.", Toast.LENGTH_SHORT).show()
        }

        folderAdapter.submitList(folderList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        _binding = null
    }
}
