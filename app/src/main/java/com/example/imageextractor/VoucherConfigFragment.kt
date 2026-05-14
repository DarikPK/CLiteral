package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentVoucherConfigBinding
import java.text.SimpleDateFormat
import java.util.*

class VoucherConfigFragment : Fragment() {

    private var _binding: FragmentVoucherConfigBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoucherConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar con fecha/hora actual si se desea
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        binding.etFecha.setText(sdf.format(Date()))

        binding.btnGenerateVoucher.setOnClickListener {
            val bundle = Bundle().apply {
                putString("zona", binding.etZona.text.toString())
                putString("oficina", binding.etOficina.text.toString())
                putString("ruc", binding.etRuc.text.toString())
                putString("local", binding.etLocal.text.toString())
                putString("recibo", binding.etRecibo.text.toString())
                putString("fecha", binding.etFecha.text.toString())
                putString("cajero", binding.etCajero.text.toString())
                putString("partida", binding.etPartida.text.toString())
                putString("paginas", binding.etPaginas.text.toString())
                putString("monto", binding.etMonto.text.toString())
                putString("presentante", binding.etPresentante.text.toString())
                putString("correo", binding.etCorreo.text.toString())
                putString("dni", binding.etDni.text.toString())
            }
            findNavController().navigate(R.id.action_voucherConfigFragment_to_voucherFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}