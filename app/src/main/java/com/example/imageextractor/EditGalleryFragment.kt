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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.FragmentEditGalleryBinding
import java.io.File

class EditGalleryFragment : Fragment() {

    private var _binding: FragmentEditGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderAdapter: FolderAdapter
    private var deleteMenuItem: MenuItem? = null

    private val storagePermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                loadFoldersFromStorage()
            } else {
                if (!shouldShowRequestPermissionRationale(storagePermission)) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Permiso Denegado")
                        .setMessage("Has denegado el permiso de forma permanente. Por favor, actívalo en los ajustes de la aplicación para poder ver tus extracciones.")
                        .setPositiveButton("Ir a Ajustes") { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", requireActivity().packageName, null)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "El permiso para leer archivos es necesario para esta función.", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Partidas Capturadas"

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermission()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter { selectionSize ->
            updateDeleteIconState(selectionSize > 0)
        }
        binding.editGalleryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
        }
    }

    private fun updateDeleteIconState(isVisible: Boolean) {
        deleteMenuItem?.isVisible = isVisible
    }

    private fun showDeleteConfirmationDialog(foldersToDelete: List<ImageFolder>) {
        val message = if (foldersToDelete.size == 1) {
            "¿Estás seguro de que deseas eliminar la partida '${foldersToDelete.first().partidaId}'?"
        } else {
            "¿Estás seguro de que deseas eliminar ${foldersToDelete.size} partidas seleccionadas?"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("$message\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                foldersToDelete.forEach { deleteFolderContents(it) }
                folderAdapter.clearSelection()
                loadFoldersFromStorage()
                Toast.makeText(context, "${foldersToDelete.size} partida(s) eliminada(s).", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteFolderContents(folder: ImageFolder) {
        folder.imagePaths.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                storagePermission
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadFoldersFromStorage()
            }
            shouldShowRequestPermissionRationale(storagePermission) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Permiso Necesario")
                    .setMessage("Para mostrar las extracciones guardadas, la aplicación necesita permiso para leer los archivos de tu dispositivo.")
                    .setPositiveButton("Entendido") { _, _ ->
                        requestPermissionLauncher.launch(storagePermission)
                    }
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
            val imageFiles = imageDir.listFiles { file ->
                file.isFile && file.name.endsWith(".png") && file.name.contains("-Hoja ")
            }

            imageFiles?.forEach { file ->
                val fileName = file.name
                val partidaId = fileName.substringBefore("-Hoja").trim()
                if (partidaId.isNotEmpty()) {
                    folders.getOrPut(partidaId) { mutableListOf() }.add(file.absolutePath)
                }
            }
        }

        val folderList = folders.map { (partidaId, paths) ->
            val sortedPaths = paths.sortedBy { path ->
                path.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0
            }
            ImageFolder(partidaId = partidaId, imagePaths = sortedPaths)
        }

        if (folderList.isEmpty()) {
            Toast.makeText(context, "No se encontraron extracciones.", Toast.LENGTH_SHORT).show()
        }

        folderAdapter.submitList(folderList)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.edit_gallery_menu, menu)
        deleteMenuItem = menu.findItem(R.id.action_delete_selection)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_selection -> {
                val selected = folderAdapter.getSelectedItems()
                if (selected.isNotEmpty()) {
                    showDeleteConfirmationDialog(selected)
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
