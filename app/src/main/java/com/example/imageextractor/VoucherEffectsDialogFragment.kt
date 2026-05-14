package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.imageextractor.databinding.DialogVoucherEffectsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VoucherEffectsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogVoucherEffectsBinding? = null
    private val binding get() = _binding!!

    var onApplyListener: ((Int, Int, Int, Int, Int, Int, Int, Boolean) -> Unit)? = null

    // Valores persistentes
    var initialWrinkles = 30
    var initialInkWear = 20
    var initialAging = 15
    var initialTextureType = 0
    var initialTextureIntensity = 30
    var initialGrain = 40
    var initialSoftness = 1
    var initialShadow = true

    private val textureTypes = arrayOf(
        "Papel suave",
        "Papel rugoso",
        "Fotocopia",
        "Documento antiguo",
        "Lienzo/Tela"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVoucherEffectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, textureTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTextureType.adapter = adapter
        binding.spinnerTextureType.setSelection(initialTextureType)

        // Cargar valores actuales
        binding.sliderWrinkles.value = initialWrinkles.toFloat()
        binding.sliderInkWear.value = initialInkWear.toFloat()
        binding.sliderAging.value = initialAging.toFloat()
        binding.sliderTextureIntensity.value = initialTextureIntensity.toFloat()
        binding.sliderGrain.value = initialGrain.toFloat()
        binding.sliderSoftness.value = initialSoftness.toFloat()
        binding.switchShadow.isChecked = initialShadow

        binding.btnApplyEffects.setOnClickListener {
            onApplyListener?.invoke(
                binding.sliderWrinkles.value.toInt(),
                binding.sliderInkWear.value.toInt(),
                binding.sliderAging.value.toInt(),
                binding.spinnerTextureType.selectedItemPosition,
                binding.sliderTextureIntensity.value.toInt(),
                binding.sliderGrain.value.toInt(),
                binding.sliderSoftness.value.toInt(),
                binding.switchShadow.isChecked
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}