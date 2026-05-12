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

    var onApplyListener: ((Int, Int, Int, Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVoucherEffectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnApplyEffects.setOnClickListener {
            onApplyListener?.invoke(
                binding.sliderWrinkles.value.toInt(),
                binding.sliderInkWear.value.toInt(),
                binding.sliderAging.value.toInt(),
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