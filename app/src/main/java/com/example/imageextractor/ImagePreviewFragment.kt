package com.example.imageextractor

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.imageextractor.databinding.FragmentImagePreviewBinding
import java.io.File

class ImagePreviewFragment : Fragment() {

    private var _binding: FragmentImagePreviewBinding? = null
    private val binding get() = _binding!!

    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            imagePath = it.getString("imagePath")
        }
    }

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
        setupZoomButtons()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.title = imagePath?.substringAfterLast("/") ?: "Vista Previa"
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadImage() {
        imagePath?.let {
            Glide.with(this)
                .load(Uri.fromFile(File(it)))
                .into(binding.previewImageView)
        }
    }

    private fun setupZoomButtons() {
        binding.fabZoomIn.setOnClickListener {
            binding.previewImageView.scaleX *= 1.2f
            binding.previewImageView.scaleY *= 1.2f
        }

        binding.fabZoomReset.setOnClickListener {
            binding.previewImageView.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
