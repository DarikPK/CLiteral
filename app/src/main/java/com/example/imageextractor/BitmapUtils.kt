package com.example.imageextractor

import android.graphics.*
import java.util.Random

fun applyInkWear(source: Bitmap, intensity: Float, size: Float, seed: Long): Bitmap {
    if (intensity <= 0.0f) return source

    val maskBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    maskBitmap.density = source.density
    val maskCanvas = Canvas(maskBitmap)
    maskCanvas.drawColor(Color.WHITE)

    val erasePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val random = Random(seed)

    // Re-calibrated formula for a more pronounced 'middle ground' effect
    val baseSize = 2.0f + size * 25f
    val intensityMultiplier = 0.2f + intensity * 4.0f

    // Layer 1: Fine grain noise
    val noiseCount = (source.width * source.height / 50 * intensityMultiplier).toInt()
    for (i in 0..noiseCount) {
        val x = random.nextFloat() * source.width
        val y = random.nextFloat() * source.height
        val radius = random.nextFloat() * (baseSize * 0.4f)
        maskCanvas.drawCircle(x, y, radius, erasePaint)
    }

    // Layer 2: Irregular blotches
    val blotchCount = (40 * intensityMultiplier).toInt()
    for (i in 0..blotchCount) {
        val path = Path()
        val startX = random.nextFloat() * source.width
        val startY = random.nextFloat() * source.height
        path.moveTo(startX, startY)

        val segmentCount = random.nextInt(5) + 4
        for (j in 0..segmentCount) {
            val pathSize = baseSize * 15f
            val cpx1 = startX + random.nextFloat() * pathSize - (pathSize / 2)
            val cpy1 = startY + random.nextFloat() * pathSize - (pathSize / 2)
            val x2 = startX + random.nextFloat() * pathSize - (pathSize / 2)
            val y2 = startY + random.nextFloat() * pathSize - (pathSize / 2)
            path.quadTo(cpx1, cpy1, x2, y2)
        }
        path.close()
        maskCanvas.drawPath(path, erasePaint)
    }

    // 5. Combinar el sello con la máscara generada.
    val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    resultBitmap.density = source.density
    val resultCanvas = Canvas(resultBitmap)
    resultCanvas.drawBitmap(source, 0f, 0f, null)

    // DST_IN mantiene los píxeles del destino (sello) solo donde los píxeles de origen (máscara) son opacos.
    // Como perforamos agujeros transparentes en la máscara, esas partes del sello se borrarán.
    val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    resultCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)

    maskBitmap.recycle()

    return resultBitmap
}
