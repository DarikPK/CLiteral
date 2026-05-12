package com.example.imageextractor

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.imageextractor.databinding.FragmentVoucherBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

class VoucherFragment : Fragment() {

    private var _binding: FragmentVoucherBinding? = null
    private val binding get() = _binding!!
    private var processedBitmap: Bitmap? = null

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

        binding.btnEffects.setOnClickListener {
            val dialog = VoucherEffectsDialogFragment()
            dialog.onApplyListener = { wrinkles, inkWear, aging, hasShadow ->
                applyRealismEffects(wrinkles, inkWear, aging, hasShadow)
            }
            dialog.show(parentFragmentManager, "VoucherEffects")
        }

        binding.btnShare.setOnClickListener {
            shareVoucher()
        }
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
            tvPaginas.text = "Paginas: ${args?.getString("paginas") ?: "7"}"
            val montoRaw = args?.getString("monto") ?: "59.60"
            tvMonto.text = "Monto S/ $montoRaw"
            tvMontoTotal.text = "Monto Total S/ $montoRaw"
            tvPresentante.text = "PRESENTANTE: ${args?.getString("presentante") ?: "LACHIRA SIAPO, PAUL DAVID"}"
            tvCorreo.text = "CORREO: ${args?.getString("correo") ?: "DAVID.LACHIRA@GMAIL.COM"}"
            tvDni.text = "DNI. - ${args?.getString("dni") ?: "46736604"}"
        }
    }

    private fun applyRealismEffects(wrinkles: Int, inkWear: Int, aging: Int, hasShadow: Boolean) {
        val originalView = binding.cvVoucher
        val bitmap = Bitmap.createBitmap(originalView.width, originalView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        originalView.draw(canvas)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Efecto Amarilleo (Papel viejo)
        if (aging > 0) {
            val agingColor = Color.argb((aging * 1.5).toInt(), 255, 230, 150)
            resultCanvas.drawColor(agingColor)
        } else {
            resultCanvas.drawColor(Color.WHITE)
        }

        // 2. Dibujar el contenido original con transparencia leve para mezclar
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        resultCanvas.drawBitmap(bitmap, 0f, 0f, paint)
        paint.xfermode = null

        // 3. Efecto de Desgaste de Tinta (Ruido/Grisáceo)
        if (inkWear > 0) {
            val noisePaint = Paint()
            noisePaint.alpha = (inkWear * 0.8).toInt()
            noisePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            val random = Random()
            for (i in 0 until (bitmap.width * bitmap.height / 100)) {
                val x = random.nextInt(bitmap.width).toFloat()
                val y = random.nextInt(bitmap.height).toFloat()
                resultCanvas.drawPoint(x, y, noisePaint)
            }
        }

        // 4. Efecto de Complexión/Arrugas (Simulado con sombras y luces)
        if (wrinkles > 0) {
            val wrinklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            wrinklePaint.style = Paint.Style.STROKE
            wrinklePaint.strokeWidth = 2f
            val random = Random(42) // Semilla fija para consistencia
            for (i in 0 until wrinkles / 5) {
                val path = Path()
                val startX = random.nextInt(result.width).toFloat()
                val startY = random.nextInt(result.height).toFloat()
                path.moveTo(startX, startY)
                path.lineTo(startX + random.nextInt(100) - 50, startY + random.nextInt(100) - 50)

                wrinklePaint.color = Color.argb((wrinkles * 0.5).toInt(), 0, 0, 0)
                resultCanvas.drawPath(path, wrinklePaint)

                wrinklePaint.color = Color.argb((wrinkles * 0.3).toInt(), 255, 255, 255)
                resultCanvas.drawPath(path, wrinklePaint)
            }
        }

        // 5. Sombra de complexión (Gradiente suave para simular doblez)
        if (hasShadow) {
            val shadowPaint = Paint()
            val gradient = LinearGradient(0f, 0f, result.width.toFloat(), result.height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(40, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0.2f, 0.5f, 0.8f), Shader.TileMode.CLAMP)
            shadowPaint.shader = gradient
            resultCanvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), shadowPaint)
        }

        processedBitmap = result
        binding.ivProcessedVoucher.setImageBitmap(result)
        binding.ivProcessedVoucher.visibility = View.VISIBLE
        binding.cvVoucher.visibility = View.GONE
    }

    private fun shareVoucher() {
        val bitmapToShare = processedBitmap ?: run {
            val view = binding.cvVoucher
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            view.draw(c)
            b
        }

        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/voucher.png")
            bitmapToShare.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val imagePath = File(requireContext().cacheDir, "images")
            val newFile = File(imagePath, "voucher.png")
            val contentUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", newFile)

            if (contentUri != null) {
                val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                shareIntent.setDataAndType(contentUri, requireContext().contentResolver.getType(contentUri))
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
                startActivity(Intent.createChooser(shareIntent, "Compartir Voucher"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}