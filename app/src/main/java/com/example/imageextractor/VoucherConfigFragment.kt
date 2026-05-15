package com.example.imageextractor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.imageextractor.databinding.FragmentVoucherConfigBinding
import java.text.SimpleDateFormat
import java.util.*

class VoucherConfigFragment : Fragment() {

    private var _binding: FragmentVoucherConfigBinding? = null
    private val binding get() = _binding!!

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("PdfSettings", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoucherConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadDataFromPdfSettings()

        binding.etMonto.doOnTextChanged { text, _, _, _ ->
            sharedPrefs.edit().putString("voucher_monto", text.toString()).apply()
        }

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
                putString("publicidad", binding.etPublicidad.text.toString())
                putString("tipo_partida", sharedPrefs.getString("dynamic_tipo_partida", "PREDIOS"))
            }
            findNavController().navigate(R.id.action_voucherConfigFragment_to_voucherFragment, bundle)
        }
    }

    private fun loadDataFromPdfSettings() {
        // 0. Tipo de Partida (para el servicio)
        val tipoPartidaPos = sharedPrefs.getInt("dynamic_tipo_partida_position", 0)
        val tipoPartidaArray = resources.getStringArray(R.array.tipo_partida_array)
        val tipoPartidaText = if (tipoPartidaPos < tipoPartidaArray.size) tipoPartidaArray[tipoPartidaPos] else "PREDIOS"
        sharedPrefs.edit().putString("dynamic_tipo_partida", tipoPartidaText).apply()

        // 1. Fecha y Hora (tal cual la del menú Generar PDF)
        val now = Calendar.getInstance()
        val dayStr = sharedPrefs.getString("stamp_day", String.format("%02d", now.get(Calendar.DAY_OF_MONTH)))
        val monthPos = sharedPrefs.getInt("stamp_month_position", now.get(Calendar.MONTH))
        val yearStr = sharedPrefs.getString("stamp_year", now.get(Calendar.YEAR).toString())
        val horaStr = sharedPrefs.getString("dynamic_hora", "08:00:00")

        // Formatear fecha: dd/MM/yyyy
        val day = dayStr?.padStart(2, '0') ?: String.format("%02d", now.get(Calendar.DAY_OF_MONTH))
        val month = (monthPos + 1).toString().padStart(2, '0')
        val fullDate = "$day/$month/$yearStr $horaStr"
        binding.etFecha.setText(fullDate)

        // 2. Número de Recibo (Año + Digito 1 + Digito 2) -> Formato AÑO-D1-D2
        val dAno = sharedPrefs.getString("dynamic_ano", yearStr)
        val d1 = sharedPrefs.getString("dynamic_digito1", "")
        val d2 = sharedPrefs.getString("dynamic_digito2", "")
        if (!d1.isNullOrBlank() && !d2.isNullOrBlank()) {
            binding.etRecibo.setText("$dAno-$d1-$d2")
        }

        // 3. Número de Publicidad (Año + Numero Publicidad) -> Formato AÑO-NUM
        val dNumPub = sharedPrefs.getString("dynamic_numero_publicidad", "")
        if (!dNumPub.isNullOrBlank()) {
            binding.etPublicidad.setText("$dAno-$dNumPub")
        }

        // 4. Partida Seleccionada
        val selPartida = sharedPrefs.getString("selected_partida_id", "")
        if (!selPartida.isNullOrBlank()) {
            binding.etPartida.setText(selPartida)
        }

        // 5. Cantidad de Páginas
        val selPages = sharedPrefs.getInt("selected_partida_pages", 0)
        if (selPages > 0) {
            binding.etPaginas.setText(selPages.toString())
        }

        // 6. Monto Persistente
        val savedMonto = sharedPrefs.getString("voucher_monto", "59.60")
        binding.etMonto.setText(savedMonto)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}