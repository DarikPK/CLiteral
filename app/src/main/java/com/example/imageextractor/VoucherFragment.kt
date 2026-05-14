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

class VoucherFragment : Fragment() {

    private var _binding: FragmentVoucherBinding? = null
    private val binding get() = _binding!!
    private var processedBitmap: Bitmap? = null

    private var currentWrinkles = 30
    private var currentInkWear = 40
    private var currentAging = 15
    private var currentWearIntensity = 40
    private var currentWearSize = 3
    private var currentShadow = true

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
            val dialog = VoucherEffectsDialogFragment().apply {
                initialWrinkles = currentWrinkles
                initialInkWear = currentInkWear
                initialAging = currentAging
                initialWearIntensity = currentWearIntensity
                initialWearSize = currentWearSize
                initialShadow = currentShadow

                onApplyListener = { w, i, a, wi, ws, s ->
                    currentWrinkles = w
                    currentInkWear = i
                    currentAging = a
                    currentWearIntensity = wi
                    currentWearSize = ws
                    currentShadow = s
                    applyRealismEffects()
                }
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

    private fun applyRealismEffects() {
        val originalView = binding.cvVoucher
        val width = originalView.width
        val height = originalView.height

        if (width <= 0 || height <= 0) return

        val originalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalCanvas = Canvas(originalBitmap)
        originalView.draw(originalCanvas)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Color Base (con Amarilleo)
        val baseColor = if (currentAging > 0) {
            // Mezcla de crema/sepia según intensidad
            val alpha = (currentAging * 2.55).toInt().coerceIn(0, 255)
            Color.argb(255, 255, (255 - (alpha * 0.1)).toInt(), (255 - (alpha * 0.3)).toInt())
        } else {
            Color.WHITE
        }
        canvas.drawColor(baseColor)

        // 2. Dibujar Contenido Original con modo MULTIPLY para teñir con el fondo
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        paint.xfermode = null

        val random = Random(42)

        // 3. Desgaste de Tinta (Hacerla más clara/grisácea irregularmente)
        if (currentInkWear > 0) {
            val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            inkPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            // Color de la hoja para "borrar" tinta
            inkPaint.color = baseColor
            inkPaint.alpha = (currentInkWear * 1.5).toInt().coerceIn(0, 255)

            for (i in 0 until (width * height / 5000) * currentInkWear / 10) {
                val x = random.nextInt(width).toFloat()
                val y = random.nextInt(height).toFloat()
                val radius = random.nextFloat() * 20f + 5f
                canvas.drawCircle(x, y, radius, inkPaint)
            }
        }

        // 4. Desgaste Físico / Ruido Orgánico (Manchas blancas/claras)
        if (currentWearIntensity > 0) {
            val wearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            wearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

            for (i in 0 until (width * height / 10000) * currentWearIntensity / 5) {
                val x = random.nextInt(width).toFloat()
                val y = random.nextInt(height).toFloat()

                // Formas más orgánicas que simples puntos
                val path = Path()
                val size = (random.nextFloat() * currentWearSize * 4) + 2
                path.addOval(x, y, x + size * 1.5f, y + size, Path.Direction.CW)

                canvas.save()
                canvas.rotate(random.nextInt(360).toFloat(), x, y)
                canvas.drawPath(path, wearPaint)
                canvas.restore()
            }
        }

        // 5. Arrugas y Dobleces (Líneas orgánicas con luz y sombra)
        if (currentWrinkles > 0) {
            val wrinklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            wrinklePaint.style = Paint.Style.STROKE
            wrinklePaint.strokeWidth = 1.5f

            for (i in 0 until currentWrinkles / 10 + 1) {
                val path = Path()
                val startX = random.nextInt(width).toFloat()
                val startY = random.nextInt(height).toFloat()
                path.moveTo(startX, startY)

                // Crear línea sinuosa
                var lastX = startX
                var lastY = startY
                val segments = 5
                for (j in 0 until segments) {
                    val nextX = lastX + random.nextInt(200) - 100
                    val nextY = lastY + random.nextInt(200) - 100
                    path.quadTo(lastX, lastY, nextX, nextY)
                    lastX = nextX
                    lastY = nextY
                }

                // Sombra de la arruga
                wrinklePaint.color = Color.argb((currentWrinkles * 0.4).toInt(), 0, 0, 0)
                canvas.drawPath(path, wrinklePaint)

                // Luz de la arruga (desplazada 1px)
                canvas.save()
                canvas.translate(1f, 1f)
                wrinklePaint.color = Color.argb((currentWrinkles * 0.3).toInt(), 255, 255, 255)
                canvas.drawPath(path, wrinklePaint)
                canvas.restore()
            }
        }

        // 6. Sombra de Complexión (Gradiente que simula papel no plano)
        if (currentShadow) {
            val shadowPaint = Paint()
            val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(50, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0.1f, 0.45f, 0.9f), Shader.TileMode.CLAMP)
            shadowPaint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadowPaint)
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
            val file = File(cachePath, "voucher.png")
            val stream = FileOutputStream(file)
            bitmapToShare.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)

            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/png"
                }
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