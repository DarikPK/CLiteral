package com.example.pdfcreator

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.example.pdfcreator.ui.main.SharedViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random


class PdfPreviewFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var imageView: ZoomableImageView
    private lateinit var stampOverlayView: StampOverlayView
    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var currentStampBitmap: Bitmap? = null
    private var originalStampBitmap: Bitmap? = null
    private var wearEffectApplied = false
    private var wearSeed = System.currentTimeMillis()


    // ActivityResultLauncher para seleccionar una imagen de la galería
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
            originalBitmap = bitmap
            currentBitmap = bitmap
            imageView.setImageBitmap(currentBitmap)
            sharedViewModel.setCurrentImage(currentBitmap)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pdf_preview, container, false)

        imageView = view.findViewById(R.id.image_preview)
        stampOverlayView = view.findViewById(R.id.stamp_overlay)

        val selectImageButton: Button = view.findViewById(R.id.button_select_image)
        selectImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val shareButton: FloatingActionButton = view.findViewById(R.id.fab_share)
        shareButton.setOnClickListener {
            shareCurrentBitmap()
        }

        // Observa la imagen del sello desde el ViewModel
        sharedViewModel.stampBitmap.observe(viewLifecycleOwner, Observer { bitmap ->
            originalStampBitmap = bitmap
            currentStampBitmap = bitmap
            stampOverlayView.setStampBitmap(currentStampBitmap)
            wearSeed = System.currentTimeMillis() // Reset seed when stamp changes
            applyAdjustments() // Aplicar ajustes actuales si los hay
        })

        imageView.onMatrixChangedListener = {
            stampOverlayView.updateMatrix(imageView.imageMatrix)
        }

        setupControls(view)

        return view
    }

    private fun setupControls(view: View) {
        val brightnessSeekBar: SeekBar = view.findViewById(R.id.seekbar_brightness)
        val contrastSeekBar: SeekBar = view.findViewById(R.id.seekbar_contrast)
        val brightnessValue: TextView = view.findViewById(R.id.textview_brightness_value)
        val contrastValue: TextView = view.findViewById(R.id.textview_contrast_value)

        val stampBrightnessSeekBar: SeekBar = view.findViewById(R.id.seekbar_stamp_brightness)
        val stampContrastSeekBar: SeekBar = view.findViewById(R.id.seekbar_stamp_contrast)
        val stampBrightnessValue: TextView = view.findViewById(R.id.textview_stamp_brightness_value)
        val stampContrastValue: TextView = view.findViewById(R.id.textview_stamp_contrast_value)
        val wearSeekBar: SeekBar = view.findViewById(R.id.seekbar_wear)
        val wearValue: TextView = view.findViewById(R.id.textview_wear_value)
        val wearButton: Button = view.findViewById(R.id.button_apply_wear)

        // Listeners para imagen
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessValue.text = progress.toString()
                sharedViewModel.setImageBrightness(progress)
                applyAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        contrastSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                contrastValue.text = progress.toString()
                sharedViewModel.setImageContrast(progress)
                applyAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Listeners para el sello
        stampBrightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                stampBrightnessValue.text = progress.toString()
                sharedViewModel.setStampBrightness(progress)
                applyAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        stampContrastSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                stampContrastValue.text = progress.toString()
                sharedViewModel.setStampContrast(progress)
                applyAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        wearSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                wearValue.text = progress.toString()
                sharedViewModel.setWearLevel(progress)
                // Apply wear immediately if the button is active
                if (wearEffectApplied) {
                    applyAdjustments()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        wearButton.setOnClickListener {
            wearEffectApplied = !wearEffectApplied
            if (wearEffectApplied) {
                wearButton.text = "Desgaste Aplicado"
                wearButton.isActivated = true
                wearSeed = System.currentTimeMillis() // Generate a new seed each time it's toggled ON
            } else {
                wearButton.text = "Aplicar Desgaste"
                wearButton.isActivated = false
            }
            applyAdjustments()
        }

        // Set initial values from ViewModel
        brightnessSeekBar.progress = sharedViewModel.imageBrightness.value ?: 50
        contrastSeekBar.progress = sharedViewModel.imageContrast.value ?: 50
        stampBrightnessSeekBar.progress = sharedViewModel.stampBrightness.value ?: 50
        stampContrastSeekBar.progress = sharedViewModel.stampContrast.value ?: 50
        wearSeekBar.progress = sharedViewModel.wearLevel.value ?: 0


        brightnessValue.text = brightnessSeekBar.progress.toString()
        contrastValue.text = contrastSeekBar.progress.toString()
        stampBrightnessValue.text = stampBrightnessSeekBar.progress.toString()
        stampContrastValue.text = stampContrastSeekBar.progress.toString()
        wearValue.text = wearSeekBar.progress.toString()
    }


    private fun applyAdjustments() {
        var adjustedBitmap = originalBitmap
        if (adjustedBitmap != null) {
            val brightness = sharedViewModel.imageBrightness.value ?: 50
            val contrast = sharedViewModel.imageContrast.value ?: 50
            if (brightness != 50 || contrast != 50) {
                adjustedBitmap = applyBrightnessContrast(adjustedBitmap!!, brightness, contrast)
            }
        }
        currentBitmap = adjustedBitmap
        imageView.setImageBitmap(currentBitmap)

        var adjustedStampBitmap = originalStampBitmap
        if (adjustedStampBitmap != null) {
            val stampBrightness = sharedViewModel.stampBrightness.value ?: 50
            val stampContrast = sharedViewModel.stampContrast.value ?: 50

            if (stampBrightness != 50 || stampContrast != 50) {
                adjustedStampBitmap = applyBrightnessContrast(adjustedStampBitmap!!, stampBrightness, stampContrast)
            }

            if (wearEffectApplied) {
                val wearLevel = sharedViewModel.wearLevel.value ?: 0
                if (wearLevel > 0) {
                    adjustedStampBitmap = applyInkWear(adjustedStampBitmap!!, wearLevel / 100f, wearSeed)
                }
            }
        }
        currentStampBitmap = adjustedStampBitmap
        stampOverlayView.setStampBitmap(currentStampBitmap)
    }

    private fun applyBrightnessContrast(sourceBitmap: Bitmap, brightness: Int, contrast: Int): Bitmap {
        val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
        resultBitmap.density = sourceBitmap.density
        val canvas = Canvas(resultBitmap)
        val paint = Paint()
        val cm = ColorMatrix()

        // Ajuste de brillo (-100 a 100)
        val brightnessF = (brightness - 50) * 2f
        // Ajuste de contraste (0 a 10)
        val contrastF = (contrast / 50f) + 1f

        cm.set(floatArrayOf(
            contrastF, 0f, 0f, 0f, brightnessF,
            0f, contrastF, 0f, 0f, brightnessF,
            0f, 0f, contrastF, 0f, brightnessF,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
        return resultBitmap
    }


    /**
     * Aplica un efecto de desgaste de tinta procedural a un bitmap.
     *
     * @param source El bitmap original del sello.
     * @param intensity La intensidad del efecto (0.0 a 1.0).
     * @param seed La semilla para el generador de números aleatorios para resultados reproducibles.
     * @return Un nuevo bitmap con el efecto de desgaste aplicado.
     */
    fun applyInkWear(source: Bitmap, intensity: Float, seed: Long): Bitmap {
        if (intensity <= 0.0f) return source

        // 1. Crear bitmaps de resultado y máscara, asegurando la misma densidad.
        val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        resultBitmap.density = source.density
        val maskBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        maskBitmap.density = source.density

        val maskCanvas = Canvas(maskBitmap)
        maskCanvas.drawColor(Color.BLACK) // Fondo opaco para la máscara

        val random = Random(seed)

        // 2. Capa 1: Ruido Perlin para simular la porosidad y grano fino.
        val noisePaint = Paint().apply {
            isAntiAlias = true
        }
        val noisePixels = IntArray(source.width * source.height)
        val perlin = PerlinNoise(seed)
        val scale = 10.0 + (1.0 - intensity) * 40.0 // A menor intensidad, mayor escala (ruido más suave)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val n = perlin.noise(x / scale, y / scale, 0.8) // Valor entre -1 y 1
                val color = ((n + 1) / 2 * 255).toInt() // Mapear a 0-255
                val alpha = (255 * (1.0f - intensity) * 0.5f).toInt() // La intensidad controla la opacidad del ruido
                noisePixels[y * source.width + x] = Color.argb(alpha, color, color, color)
            }
        }
        maskBitmap.setPixels(noisePixels, 0, source.width, 0, 0, source.width, source.height)


        // 3. Capa 2: Manchas irregulares para simular áreas de menor adherencia.
        val blotchPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val blotchCount = (20 * intensity).toInt()
        for (i in 0..blotchCount) {
            val path = Path()
            val startX = random.nextFloat() * source.width
            val startY = random.nextFloat() * source.height
            path.moveTo(startX, startY)

            val segmentCount = random.nextInt(3, 8)
            for (j in 0..segmentCount) {
                val cpx1 = startX + random.nextFloat() * 80 - 40
                val cpy1 = startY + random.nextFloat() * 80 - 40
                val x2 = startX + random.nextFloat() * 80 - 40
                val y2 = startY + random.nextFloat() * 80 - 40
                path.quadTo(cpx1, cpy1, x2, y2)
            }
            path.close()

            val blotchAlpha = (random.nextFloat() * 180 * intensity).toInt()
            val blotchColor = Color.argb(blotchAlpha, 255, 255, 255)
            blotchPaint.color = blotchColor
            maskCanvas.drawPath(path, blotchPaint)
        }

        // 4. Combinar la máscara con la imagen original.
        val resultCanvas = Canvas(resultBitmap)
        // Dibuja la imagen original
        resultCanvas.drawBitmap(source, 0f, 0f, null)

        // Usa la máscara para "borrar" partes de la imagen original
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        resultCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)

        return resultBitmap
    }


    private fun shareCurrentBitmap() {
        val finalBitmap = getFinalBitmap() ?: return

        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_image.png")
            val fileOutputStream = FileOutputStream(file)
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()

            val contentUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND)
            shareIntent.type = "image/png"
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            startActivity(android.content.Intent.createChooser(shareIntent, "Compartir imagen"))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFinalBitmap(): Bitmap? {
        if (currentBitmap == null) return null

        // Crear un bitmap con el tamaño de la imagen base
        val finalBitmap = Bitmap.createBitmap(currentBitmap!!.width, currentBitmap!!.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Dibujar la imagen base
        canvas.drawBitmap(currentBitmap!!, 0f, 0f, null)

        // Si hay un sello, dibujarlo encima
        if (currentStampBitmap != null) {
            val stampPosition = stampOverlayView.getStampPosition()
            val stampMatrix = Matrix()
            // Escalar el sello para que coincida con el zoom de la imagen
            val values = FloatArray(9)
            imageView.imageMatrix.getValues(values)
            val scaleX = values[Matrix.MSCALE_X]
            val scaleY = values[Matrix.MSCALE_Y]
            stampMatrix.postScale(scaleX, scaleY)

            // Posicionar el sello
            stampMatrix.postTranslate(stampPosition.x, stampPosition.y)

            canvas.drawBitmap(currentStampBitmap!!, stampMatrix, null)
        }

        return finalBitmap
    }

}

/**
 * Clase para generar ruido Perlin. Es útil para crear texturas procedurales naturales.
 */
class PerlinNoise(private val seed: Long) {
    private val p = IntArray(512)

    init {
        val random = Random(seed)
        val permutation = IntArray(256) { it }
        for (i in 255 downTo 0) {
            val index = random.nextInt(i + 1)
            val temp = permutation[i]
            permutation[i] = permutation[index]
            permutation[index] = temp
        }

        for (i in 0..255) {
            p[i] = permutation[i]
            p[256 + i] = permutation[i]
        }
    }

    fun noise(x: Double, y: Double, z: Double): Double {
        val xi = x.toInt() and 255
        val yi = y.toInt() and 255
        val zi = z.toInt() and 255

        val xf = x - x.toInt()
        val yf = y - y.toInt()
        val zf = z - z.toInt()

        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)

        val a = p[xi] + yi
        val aa = p[a] + zi
        val ab = p[a + 1] + zi
        val b = p[xi + 1] + yi
        val ba = p[b] + zi
        val bb = p[b + 1] + zi

        return lerp(w, lerp(v, lerp(u, grad(p[aa], xf, yf, zf), grad(p[ba], xf - 1, yf, zf)),
            lerp(u, grad(p[ab], xf, yf - 1, zf), grad(p[bb], xf - 1, yf - 1, zf))),
            lerp(v, lerp(u, grad(p[aa + 1], xf, yf, zf - 1), grad(p[ba + 1], xf - 1, yf, zf - 1)),
                lerp(u, grad(p[ab + 1], xf, yf - 1, zf - 1), grad(p[bb + 1], xf - 1, yf - 1, zf - 1))))
    }

    private fun fade(t: Double): Double {
        return t * t * t * (t * (t * 6 - 15) + 10)
    }

    private fun lerp(t: Double, a: Double, b: Double): Double {
        return a + t * (b - a)
    }

    private fun grad(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return ((if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v))
    }
}
