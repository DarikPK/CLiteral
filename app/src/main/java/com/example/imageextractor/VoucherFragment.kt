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

        setupDynamicData()
    }

    private fun setupDynamicData() {
        val args = arguments
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        binding.apply {
            tvZona.text = args?.getString("zona") ?: "ZONA REGISTRAL Nº IX"
            tvOficina.text = args?.getString("oficina") ?: "OFICINA REGISTRAL DE LIMA"

            val rucRaw = args?.getString("ruc") ?: "20260998898"
            tvRuc.text = if (rucRaw.startsWith("RUC")) rucRaw else "RUC Nro. $rucRaw"

            tvLocal.text = "Local: ${args?.getString("local") ?: "Santa Anita"}"
            tvRecibo.text = "Recibo N° . : ${args?.getString("recibo") ?: "2025-191-8801"}"
            tvFecha.text = "Fecha/Hora: $currentDate"
            tvCajero.text = "Cajero: ${args?.getString("cajero") ?: "MONTERO MANRIQUE, MARIA DE FATIMA"}"

            tvServicio.text = "PREDIOS- CERTI. LITERAL - PREDIOS"
            tvPublicidad.text = "PUBLICIDAD N°: 2025-4809963"
            tvDestino.text = "DESTINO: ${args?.getString("destino") ?: "LIMA"}"
            tvPartida.text = "Partida: ${args?.getString("partida") ?: "49048530"}"
            tvFicha.text = "Ficha: 0000000000"
            tvTomo.text = "Tomo/Folio: 000000 /000000"
            tvPaginas.text = "Paginas: ${args?.getString("paginas") ?: "7"}"
            tvCopias.text = "Copias: 1"

            val montoRaw = args?.getString("monto") ?: "59.60"
            tvMonto.text = "Monto S/ $montoRaw"
            tvMontoTotal.text = "Monto Total S/ $montoRaw"

            tvPresentante.text = "PRESENTANTE: ${args?.getString("presentante") ?: "LACHIRA SIAPO, PAUL DAVID"}"
            tvCorreo.text = "CORREO: ${args?.getString("correo") ?: "DAVID.LACHIRA@GMAIL.COM"}"
            tvDni.text = "DNI. - ${args?.getString("dni") ?: "46736604"}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}