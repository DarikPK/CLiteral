package com.example.imageextractor

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
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

    // Permiso para LEER archivos
    private val readStoragePermission: String
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }

    // Permiso para ESCRIBIR (eliminar) archivos
    private val writeStoragePermission: String
        get() = Manifest.permission.WRITE_EXTERNAL_STORAGE

    // Launcher para el permiso de LECTURA.
    private val requestReadPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            loadFoldersFromStorage()
        } else {
            Toast.makeText(requireContext(), "Permiso denegado. No se pueden mostrar las partidas.", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher para el permiso de ESCRITURA (para eliminar).
    private val requestDeletePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permiso concedido, ahora mostramos el diálogo de confirmación.
            showDeleteConfirmationDialog()
        } else {
            Toast.makeText(requireContext(), "Permiso de escritura denegado. No se pueden eliminar los archivos.", Toast.LENGTH_LONG).show()
        }
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
        checkAndRequestPermission() // Comprueba el permiso de LECTURA al volver a la pantalla
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
                    0 -> setMode(false)
                    1 -> setMode(true)
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

    // Paso 1: Punto de entrada desde el clic en el menú
    private fun checkPermissionAndAttemptDelete() {
        if (folderAdapter.getSelectedItems().isEmpty()) {
            Toast.makeText(requireContext(), "No hay partidas seleccionadas.", Toast.LENGTH_SHORT).show()
            return
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), writeStoragePermission) == PackageManager.PERMISSION_GRANTED -> {
                // Ya tenemos permiso, mostramos el diálogo de confirmación
                showDeleteConfirmationDialog()
            }
            else -> {
                // No tenemos permiso, lo solicitamos. El resultado lo gestiona requestDeletePermissionLauncher
                requestDeletePermissionLauncher.launch(writeStoragePermission)
            }
        }
    }

    // Paso 2: Mostrar el diálogo de confirmación (asume que el permiso está o será concedido)
    private fun showDeleteConfirmationDialog() {
        val selected = folderAdapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Deseas eliminar ${selected.size} partida(s)? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteSelectedFolders()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Paso 3: La lógica de borrado en sí
    private fun deleteSelectedFolders() {
        val selected = folderAdapter.getSelectedItems()
        var totalDeletedFiles = 0
        val failedDeletions = mutableListOf<String>()

        selected.forEach { folder ->
            folder.imagePaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    if (file.delete()) {
                        totalDeletedFiles++
                    } else {
                        failedDeletions.add(file.name)
                    }
                }
            }
        }

        val context = context ?: return // Evitar crash si el fragmento se desvincula

        if (totalDeletedFiles > 0) {
            Toast.makeText(context, "$totalDeletedFiles archivo(s) eliminado(s) correctamente.", Toast.LENGTH_SHORT).show()
        }

        if (failedDeletions.isNotEmpty()) {
            Toast.makeText(context, "No se pudieron eliminar ${failedDeletions.size} archivo(s).", Toast.LENGTH_LONG).show()
        } else if (totalDeletedFiles == 0 && selected.isNotEmpty()) {
            Toast.makeText(context, "No se eliminó ningún archivo. Verifique los permisos.", Toast.LENGTH_LONG).show()
        }

        // Refrescar la UI
        folderAdapter.deselectAll()
        loadFoldersFromStorage()
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), readStoragePermission) == PackageManager.PERMISSION_GRANTED -> {
                loadFoldersFromStorage()
            }
            shouldShowRequestPermissionRationale(readStoragePermission) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Permiso Necesario")
                    .setMessage("Para leer las partidas guardadas, la aplicación necesita acceso a tus archivos.")
                    .setPositiveButton("Entendido") { _, _ ->
                        requestReadPermissionLauncher.launch(readStoragePermission)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> {
                requestReadPermissionLauncher.launch(readStoragePermission)
            }
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
                checkPermissionAndAttemptDelete() // La llamada ahora va a la nueva función
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
