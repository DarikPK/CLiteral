    private fun applyWearEffect() {
        cleanStampBitmap?.let { sourceBitmap ->
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Aplicando desgaste...", Toast.LENGTH_SHORT).show()
                }

                // Create the result bitmap with the same density as the source to prevent scaling issues
                val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
                resultBitmap.density = sourceBitmap.density

                val canvas = Canvas(resultBitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

                val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }

                val random = Random(System.currentTimeMillis())
                val baseDefects = 2000
                val numDefects = (baseDefects * stampWearIntensity).toInt()
                val sizeMultiplier = 1 + (stampWearSize / 100f) * 4
                val maxRadius = sourceBitmap.height / 12f

                for (i in 0 until numDefects) {
                    val x = random.nextFloat() * sourceBitmap.width
                    val y = random.nextFloat() * sourceBitmap.height
                    val radius = random.nextFloat() * maxRadius * (0.5f + stampWearIntensity) * sizeMultiplier
                    canvas.drawCircle(x, y, radius, erasePaint)
                }

                wornStampBitmap = resultBitmap

                withContext(Dispatchers.Main) {
                    binding.applyWearButton.isActivated = true
                    Toast.makeText(context, "Efecto de desgaste aplicado.", Toast.LENGTH_SHORT).show()
                    displayPage(currentPageIndex)
                }
            }
        }
    }

    // The generateWearMask and applyInkWearMask functions are no longer needed.
    private fun generateWearMask(width: Int, height: Int, intensity: Float, seed: Long): Bitmap {
        // This function is now unused.
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun applyInkWearMask(sourceBitmap: Bitmap, intensity: Float, seed: Long): Bitmap {
        // This function is now unused.
        return sourceBitmap
    }
