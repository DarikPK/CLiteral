package com.example.imageextractor

import android.content.ContentValues
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.drawToBitmap
import androidx.fragment.app.Fragment
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imageextractor.databinding.FragmentEditGalleryBinding
import java.io.File

data class ImageFolder(val partidaId: String, val imagePaths: List<String>)

class EditGalleryFragment : Fragment() {

    private var _binding: FragmentEditGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderAdapter: FolderAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                loadFoldersFromStorage()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // User has permanently denied the permission.
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        checkAndRequestPermission()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter { folder ->
            val bundle = Bundle().apply {
                putStringArray("imageUrls", folder.imagePaths.toTypedArray())
            }
            findNavController().navigate(R.id.action_editingFragment_to_viewGalleryFragment, bundle)
        }
        binding.editGalleryRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = folderAdapter
        }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya concedido, cargar las carpetas.
                loadFoldersFromStorage()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                // El usuario ha denegado el permiso antes. Mostrar una explicación.
                AlertDialog.Builder(requireContext())
                    .setTitle("Permiso Necesario")
                    .setMessage("Para mostrar las extracciones guardadas, la aplicación necesita permiso para leer los archivos de tu dispositivo.")
                    .setPositiveButton("Entendido") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> {
                // Pedir el permiso por primera vez o si el usuario marcó "No volver a preguntar".
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}