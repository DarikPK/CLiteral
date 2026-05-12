package com.example.imageextractor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.imageextractor.databinding.FragmentVoucherBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoucherFragment : Fragment() {

    private var _binding: FragmentVoucherBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoucherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Por ahora inicializamos con los datos de la imagen de referencia
        // pero preparados para ser dinámicos
        setupInitialData()
    }

    private fun setupInitialData() {
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        binding.apply {
            tvZona.text = "ZONA REGISTRAL Nº IX"
            tvOficina.text = "OFICINA REGISTRAL DE LIMA"
            tvRuc.text = "RUC Nro. 20260998898"

            tvLocal.text = "Local: Santa Anita"
            tvRecibo.text = "Recibo N° . : 2025-191-8801"
            tvFecha.text = "Fecha/Hora: $currentDate"
            tvCajero.text = "Cajero: MONTERO MANRIQUE, MARIA DE FATIMA"

            tvServicio.text = "PREDIOS- CERTI. LITERAL - PREDIOS"
            tvPublicidad.text = "PUBLICIDAD N°: 2025-4809963"
            tvDestino.text = "DESTINO: LIMA"
            tvPartida.text = "Partida: 49048530"
            tvFicha.text = "Ficha: 0000000000"
            tvTomo.text = "Tomo/Folio: 000000 /000000"
            tvPaginas.text = "Paginas: 7"
            tvCopias.text = "Copias: 1"
            tvMonto.text = "Monto S/ 59.60"

            tvMontoTotal.text = "Monto Total S/ 59.60"

            tvPresentante.text = "PRESENTANTE: LACHIRA SIAPO, PAUL DAVID"
            tvCorreo.text = "CORREO: DAVID.LACHIRA@GMAIL.COM"
            tvDni.text = "DNI. - 46736604"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}