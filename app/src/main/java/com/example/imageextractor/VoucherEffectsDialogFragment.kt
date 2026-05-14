package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.imageextractor.databinding.DialogVoucherEffectsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VoucherEffectsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogVoucherEffectsBinding? = null
    private val binding get() = _binding!!

    var onApplyListener: ((Int, Int, Int, Int, Int, Boolean) -> Unit)? = null

    var initialWrinkles = 30
    var initialInkWear = 20
    var initialAging = 15
    var initialWearIntensity = 40
    var initialWearSize = 3
    var initialShadow = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVoucherEffectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cargar valores actuales
        binding.sliderWrinkles.value = initialWrinkles.toFloat()
        binding.sliderInkWear.value = initialInkWear.toFloat()
        binding.sliderAging.value = initialAging.toFloat()
        binding.sliderWearIntensity.value = initialWearIntensity.toFloat()
        binding.sliderWearSize.value = initialWearSize.toFloat()
        binding.switchShadow.isChecked = initialShadow

        binding.btnApplyEffects.setOnClickListener {
            onApplyListener?.invoke(
                binding.sliderWrinkles.value.toInt(),
                binding.sliderInkWear.value.toInt(),
                binding.sliderAging.value.toInt(),
                binding.sliderWearIntensity.value.toInt(),
                binding.sliderWearSize.value.toInt(),
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