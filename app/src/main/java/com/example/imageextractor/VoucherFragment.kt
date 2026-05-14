package com.example.imageextractor

import android.content.Context
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
import androidx.lifecycle.lifecycleScope
import com.example.imageextractor.databinding.FragmentVoucherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class VoucherFragment : Fragment() {

    private var _binding: FragmentVoucherBinding? = null
    private val binding get() = _binding!!
    private var processedBitmap: Bitmap? = null

    // Variables para persistir efectos (con valores por defecto sutiles)
    private var currentWrinkles = 15
    private var currentInkWear = 10
    private var currentAging = 5
    private var currentTextureType = 0
    private var currentTextureIntensity = 20
    private var currentGrain = 15
    private var currentSoftness = 1
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
        loadPresets()
        setupDynamicData()

        binding.btnEffects.setOnClickListener {
            val dialog = VoucherEffectsDialogFragment().apply {
                initialWrinkles = currentWrinkles
                initialInkWear = currentInkWear
                initialAging = currentAging
                initialTextureType = currentTextureType
                initialTextureIntensity = currentTextureIntensity
                initialGrain = currentGrain
                initialSoftness = currentSoftness
                initialShadow = currentShadow

                onApplyListener = { w, i, a, tt, ti, g, s, sh ->
                    currentWrinkles = w
                    currentInkWear = i
                    currentAging = a
                    currentTextureType = tt
                    currentTextureIntensity = ti
                    currentGrain = g
                    currentSoftness = s
                    currentShadow = sh
                    savePresets()
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
            tvFecha.text = "Fecha/Hora: ${args?.getString("fecha") ?: currentDate}"
            tvCajero.text = "Cajero: ${args?.getString("cajero") ?: "MONTERO MANRIQUE, MARIA DE FATIMA"}"
            tvServicio.text = "PREDIOS- CERTI. LITERAL - PREDIOS"
            tvPublicidad.text = "PUBLICIDAD N°: ${args?.getString("publicidad") ?: "2025-4809963"}"
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

        // Aplicar efectos iniciales cargados
        binding.root.post { applyRealismEffects() }
    }

    private fun applyRealismEffects() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                renderVoucherWithEffects()
            }
            result?.let {
                processedBitmap = it
                binding.ivProcessedVoucher.setImageBitmap(it)
                binding.ivProcessedVoucher.visibility = View.VISIBLE
                binding.cvVoucher.visibility = View.GONE
            }
        }
    }

    private fun renderVoucherWithEffects(): Bitmap? {
        val originalView = binding.cvVoucher
        val width = originalView.width
        val height = originalView.height
        if (width <= 0 || height <= 0) return null

        val originalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalCanvas = Canvas(originalBitmap)
        originalView.draw(originalCanvas)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Color Base (Amarilleo sutil)
        val agingAlpha = (currentAging * 1.5).toInt().coerceIn(0, 255)
        val baseColor = Color.argb(255, 255, (255 - agingAlpha * 0.1).toInt(), (255 - agingAlpha * 0.2).toInt())
        canvas.drawColor(baseColor)

        // 2. Dibujar Contenido
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        paint.xfermode = null

        val random = Random(42)

        // 3. Texturizador Avanzado (Simulación de papel y grano)
        if (currentTextureIntensity > 0) {
            applyTexturizer(canvas, width, height, random)
        }

        // 4. Desvanecimiento de Tinta
        if (currentInkWear > 0) {
            val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            inkPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            inkPaint.color = baseColor
            inkPaint.alpha = (currentInkWear * 1.2).toInt().coerceIn(0, 255)
            for (i in 0 until (width * height / 8000) * currentInkWear / 10) {
                val x = random.nextInt(width).toFloat()
                val y = random.nextInt(height).toFloat()
                canvas.drawCircle(x, y, random.nextFloat() * 15f + 2f, inkPaint)
            }
        }

        // 5. Arrugas Orgánicas
        if (currentWrinkles > 0) {
            val wrinklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            wrinklePaint.style = Paint.Style.STROKE
            wrinklePaint.strokeWidth = 1.2f
            for (i in 0 until currentWrinkles / 12 + 1) {
                val path = Path()
                val startX = random.nextInt(width).toFloat()
                val startY = random.nextInt(height).toFloat()
                path.moveTo(startX, startY)
                var lx = startX
                var ly = startY
                for (j in 0 until 4) {
                    val nx = lx + random.nextInt(250) - 125
                    val ny = ly + random.nextInt(250) - 125
                    path.quadTo(lx, ly, nx, ny)
                    lx = nx
                    ly = ny
                }
                wrinklePaint.color = Color.argb((currentWrinkles * 0.3).toInt(), 0, 0, 0)
                canvas.drawPath(path, wrinklePaint)
                canvas.save()
                canvas.translate(0.5f, 0.5f)
                wrinklePaint.color = Color.argb((currentWrinkles * 0.2).toInt(), 255, 255, 255)
                canvas.drawPath(path, wrinklePaint)
                canvas.restore()
            }
        }

        // 6. Sombra de Complexión
        if (currentShadow) {
            val shadowPaint = Paint()
            val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(35, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0.15f, 0.5f, 0.85f), Shader.TileMode.CLAMP)
            shadowPaint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadowPaint)
        }

        return result
    }

    private fun applyTexturizer(canvas: Canvas, width: Int, height: Int, random: Random) {
        val grainPaint = Paint()
        val intensity = (currentTextureIntensity * 0.6).toInt()

        // Grano Fino (Simulación de scanner/fotocopia)
        if (currentGrain > 0) {
            grainPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            for (i in 0 until (width * height / 2000) * currentGrain / 20) {
                val x = random.nextInt(width).toFloat()
                val y = random.nextInt(height).toFloat()
                val colorVal = if (random.nextBoolean()) 255 else 0
                grainPaint.color = Color.argb(intensity / 2, colorVal, colorVal, colorVal)
                canvas.drawRect(x, y, x + 1.5f, y + 1.5f, grainPaint)
            }
        }

        // Textura Orgánica según tipo
        val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        texturePaint.alpha = intensity
        when (currentTextureType) {
            1 -> { // Papel rugoso: Trazos cortos aleatorios
                texturePaint.strokeWidth = 1f
                for (i in 0 until 500) {
                    val x = random.nextInt(width).toFloat()
                    val y = random.nextInt(height).toFloat()
                    canvas.drawLine(x, y, x + random.nextInt(10), y + random.nextInt(10), texturePaint)
                }
            }
            2 -> { // Fotocopia: Variaciones de luminosidad en franjas
                val photopaint = Paint()
                photopaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, Color.argb(20, 0, 0, 0), Color.TRANSPARENT),
                    null, Shader.TileMode.REPEAT)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), photopaint)
            }
            3 -> { // Documento antiguo: Manchas sutiles
                texturePaint.style = Paint.Style.FILL
                for (i in 0 until 10) {
                    val x = random.nextInt(width).toFloat()
                    val y = random.nextInt(height).toFloat()
                    val radius = random.nextFloat() * 100f + 50f
                    val radial = RadialGradient(x, y, radius, Color.argb(15, 139, 69, 19), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    texturePaint.shader = radial
                    canvas.drawCircle(x, y, radius, texturePaint)
                }
                texturePaint.shader = null
            }
            4 -> { // Lienzo: Patrón de rejilla
                texturePaint.strokeWidth = 0.5f
                for (i in 0 until width step 6) canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), texturePaint)
                for (i in 0 until height step 6) canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), texturePaint)
            }
        }

        // Suavizado (Leve desenfoque de la textura)
        if (currentSoftness > 0) {
            // Simulado mediante una capa semitransparente que unifica tonos
            val softPaint = Paint()
            softPaint.color = Color.argb(currentSoftness * 5, 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), softPaint)
        }
    }

    private fun savePresets() {
        val prefs = requireContext().getSharedPreferences("voucher_effects", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("wrinkles", currentWrinkles)
            putInt("ink_wear", currentInkWear)
            putInt("aging", currentAging)
            putInt("texture_type", currentTextureType)
            putInt("texture_intensity", currentTextureIntensity)
            putInt("grain", currentGrain)
            putInt("softness", currentSoftness)
            putBoolean("shadow", currentShadow)
            apply()
        }
    }

    private fun loadPresets() {
        val prefs = requireContext().getSharedPreferences("voucher_effects", Context.MODE_PRIVATE)
        currentWrinkles = prefs.getInt("wrinkles", 15)
        currentInkWear = prefs.getInt("ink_wear", 10)
        currentAging = prefs.getInt("aging", 5)
        currentTextureType = prefs.getInt("texture_type", 0)
        currentTextureIntensity = prefs.getInt("texture_intensity", 20)
        currentGrain = prefs.getInt("grain", 15)
        currentSoftness = prefs.getInt("softness", 1)
        currentShadow = prefs.getBoolean("shadow", true)
    }

    private fun shareVoucher() {
        val bitmapToShare = processedBitmap ?: return
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
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}