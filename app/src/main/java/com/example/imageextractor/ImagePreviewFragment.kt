package com.example.imageextractor

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.imageextractor.databinding.FragmentImagePreviewBinding
import java.io.File

class ImagePreviewFragment : Fragment() {

    private var _binding: FragmentImagePreviewBinding? = null
    private val binding get() = _binding!!
    private val args: ImagePreviewFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        loadImage()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadImage() {
        val imagePath = args.imagePath
        val imageFile = File(imagePath)
        if (imageFile.exists()) {
            binding.previewImageView.setImageURI(Uri.fromFile(imageFile))
            (activity as? AppCompatActivity)?.supportActionBar?.title = imageFile.name
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}