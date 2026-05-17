package com.example.imageextractor

import android.Manifest
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imageextractor.databinding.FragmentEditGalleryBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditGalleryFragment : Fragment() {

    private var _binding: FragmentEditGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderAdapter: FolderAdapter
    private var deleteMenuItem: MenuItem? = null
    private var selectAllMenuItem: MenuItem? = null
    private var isSelectionMode = false
    private var currentSortMode = "temporal" // or "alphanumeric"

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", android.content.Context.MODE_PRIVATE)
    }

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    // Permiso para LEER archivos
    private val readStoragePermission: String
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }

    // Launcher para el permiso de LECTURA.
    private val requestReadPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            loadFoldersFromStorage()
        } else {
            Toast.makeText(requireContext(), "Permiso denegado. No se pueden mostrar las partidas.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) {
                Toast.makeText(requireContext(), "Archivos eliminados correctamente.", Toast.LENGTH_SHORT).show()
                loadFoldersFromStorage()
            } else {
                Toast.makeText(requireContext(), "No se pudieron eliminar todos los archivos seleccionados.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentSortMode = sharedPrefs.getString("partida_sort_mode", "temporal") ?: "temporal"

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
                        // Extraemos solo las rutas de los objetos ImageFile
                        val imagePaths = folder.imageFiles.map { it.path }.toTypedArray()
                        putStringArray("imageUrls", imagePaths)
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

    private fun showDeleteConfirmationDialog() {
        val selected = folderAdapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Deseas eliminar ${selected.size} partida(s)? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteSelectedFolders(selected)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteSelectedFolders(selectedFolders: List<ImageFolder>) {
        lifecycleScope.launch {
            val filesToDelete = selectedFolders.flatMap { it.imageFiles }
            deleteFiles(filesToDelete)

            // Refrescar UI después de borrar
            withContext(Dispatchers.Main) {
                folderAdapter.deselectAll()
                loadFoldersFromStorage()
            }
        }
    }

    private suspend fun deleteFiles(files: List<ImageFile>) {
        withContext(Dispatchers.IO) {
            val urisToDelete = files.map { it.uri }
            try {
                // Para Android 11 (R) y superior, este es el método correcto.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, urisToDelete)
                     withContext(Dispatchers.Main) {
                         intentSenderLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                     }
                } else {
                    // Para versiones antiguas, el método de File funciona con el permiso de escritura.
                    var deletedCount = 0
                    files.forEach {
                        if (File(it.path).delete()) {
                            deletedCount++
                        }
                    }
                    withContext(Dispatchers.Main) {
                         Toast.makeText(requireContext(), "$deletedCount archivo(s) eliminado(s).", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                // Maneja la excepción de seguridad, que puede ocurrir en R si no se usa el createDeleteRequest
                 withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error de seguridad. No se pudieron eliminar los archivos.", Toast.LENGTH_LONG).show()
                 }
            }
        }
    }


    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), readStoragePermission) == PackageManager.PERMISSION_GRANTED -> {
                loadFoldersFromStorage()
            }
            else -> {
                requestReadPermissionLauncher.launch(readStoragePermission)
            }
        }
    }

    private fun loadFoldersFromStorage() {
        lifecycleScope.launch(Dispatchers.IO) {
            val folders = mutableMapOf<String, MutableList<ImageFile>>()
            val folderLastModified = mutableMapOf<String, Long>()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            // Buscamos en la carpeta específica dentro de Downloads
            val selection = "${MediaStore.Images.Media.DATA} like ? and ${MediaStore.Images.Media.DATA} like ?"
            val selectionArgs = arrayOf("%/Download/capturas_sunarp/%", "%-Hoja %")

            val cursor = requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val path = it.getString(pathColumn)
                    val date = it.getLong(dateColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val partidaId = name.substringBefore("-Hoja").trim()
                    if (partidaId.isNotEmpty()) {
                        val imageFile = ImageFile(uri, path, name)
                        folders.getOrPut(partidaId) { mutableListOf() }.add(imageFile)

                        val currentMaxDate = folderLastModified[partidaId] ?: 0L
                        if (date > currentMaxDate) {
                            folderLastModified[partidaId] = date
                        }
                    }
                }
            }

            val folderList = folders.map { (partidaId, files) ->
                val sortedFiles = files.sortedBy { it.name.substringAfter("-Hoja ").substringBefore(".png").toIntOrNull() ?: 0 }
                ImageFolder(
                    partidaId = partidaId,
                    imageFiles = sortedFiles,
                    lastModified = folderLastModified[partidaId] ?: 0L
                )
            }

            val sortedList = if (currentSortMode == "temporal") {
                folderList.sortedByDescending { it.lastModified }
            } else {
                folderList.sortedBy { it.partidaId }
            }

            withContext(Dispatchers.Main) {
                folderAdapter.submitList(sortedList)
            }
        }
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
            R.id.action_sort -> {
                toggleSortMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleSortMode() {
        currentSortMode = if (currentSortMode == "alphanumeric") "temporal" else "alphanumeric"
        sharedPrefs.edit().putString("partida_sort_mode", currentSortMode).apply()
        val message = if (currentSortMode == "alphanumeric") "Orden Alfanumérico"
                     else "Orden Temporal (Reciente primero)"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        loadFoldersFromStorage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
