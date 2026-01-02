    private fun applyWearEffect() {
        cleanStampBitmap?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Aplicando desgaste...", Toast.LENGTH_SHORT).show()
                }

                // Simplified and robust wear effect logic
                val sourceBitmap = cleanStampBitmap!!
                val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resultBitmap)
                val paint = Paint()
                canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

                val erasePaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    isAntiAlias = true
                }

                val random = Random(System.currentTimeMillis())
                val baseDefects = 2000
                val numDefects = (baseDefects * (stampWearIntensity / 100f)).toInt()
                val sizeMultiplier = 1 + (stampWearSize / 100f) * 4
                val maxRadius = sourceBitmap.height / 12f

                for (i in 0 until numDefects) {
                    val x = (random.nextGaussian() * (sourceBitmap.width / 4) + (sourceBitmap.width / 2)).toFloat()
                    val y = (random.nextGaussian() * (sourceBitmap.height / 4) + (sourceBitmap.height / 2)).toFloat()
                    val baseRadius = random.nextFloat() * maxRadius * (0.5f + (stampWearIntensity/100f)) * sizeMultiplier
                    val numBlobs = random.nextInt(4) + 1
                    for (j in 0 until numBlobs) {
                        val blobX = x + (random.nextFloat() - 0.5f) * baseRadius * 2
                        val blobY = y + (random.nextFloat() - 0.5f) * baseRadius * 2
                        val blobRadius = baseRadius * (0.5f + random.nextFloat())
                        canvas.drawCircle(blobX, blobY, blobRadius, erasePaint)
                    }
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

    // The following two methods are now unused and can be removed.
    // private fun generateWearMask(...) { ... }
    // private fun applyInkWearMask(...) { ... }
