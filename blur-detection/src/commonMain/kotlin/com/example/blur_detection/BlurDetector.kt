package com.example.blur_detection

/**
 * Utility for detecting blur in images using the Laplacian variance method.
 */
object BlurDetector {
    /**
     * Calculates the variance of the Laplacian of the image.
     * Higher values indicate sharper images, lower values indicate blur.
     *
     * @param grayPixels Grayscale pixel values (0-255).
     * @param width Image width.
     * @param height Image height.
     * @return The variance of the Laplacian.
     */
    fun calculateLaplacianVariance(grayPixels: ByteArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0

        val laplacianSize = (width - 2) * (height - 2)
        var sum = 0.0
        
        val laplacianValues = DoubleArray(laplacianSize)
        
        for (y in 1 until height - 1) {
            val yOffset = y * width
            val prevYOffset = (y - 1) * width
            val nextYOffset = (y + 1) * width
            
            for (x in 1 until width - 1) {
                // Laplacian 8-neighbor kernel:
                // [[-1, -1, -1],
                //  [-1,  8, -1],
                //  [-1, -1, -1]]
                val center = (grayPixels[yOffset + x].toInt() and 0xFF) * 8
                val n1 = grayPixels[prevYOffset + x - 1].toInt() and 0xFF
                val n2 = grayPixels[prevYOffset + x].toInt() and 0xFF
                val n3 = grayPixels[prevYOffset + x + 1].toInt() and 0xFF
                val n4 = grayPixels[yOffset + x - 1].toInt() and 0xFF
                val n5 = grayPixels[yOffset + x + 1].toInt() and 0xFF
                val n6 = grayPixels[nextYOffset + x - 1].toInt() and 0xFF
                val n7 = grayPixels[nextYOffset + x].toInt() and 0xFF
                val n8 = grayPixels[nextYOffset + x + 1].toInt() and 0xFF
                
                val value = (center - (n1 + n2 + n3 + n4 + n5 + n6 + n7 + n8)).toDouble()
                val index = (y - 1) * (width - 2) + (x - 1)
                laplacianValues[index] = value
                sum += value
            }
        }
        
        val mean = sum / laplacianSize
        var varianceSum = 0.0
        for (value in laplacianValues) {
            varianceSum += (value - mean) * (value - mean)
        }
        
        return varianceSum / laplacianSize
    }

    /**
     * Calculates the average brightness of the image.
     */
    fun calculateAverageBrightness(grayPixels: ByteArray): Double {
        if (grayPixels.isEmpty()) return 0.0
        var sum = 0L
        for (pixel in grayPixels) {
            sum += pixel.toInt() and 0xFF
        }
        return sum.toDouble() / grayPixels.size
    }

    /**
     * Checks if the image is blurred based on a threshold.
     * Default threshold is increased to 300.0 for better motion blur detection with 8-neighbor kernel.
     */
    fun isBlurred(grayPixels: ByteArray, width: Int, height: Int, threshold: Double = 300.0): Boolean {
        // If the image is extremely dark (e.g. covered), we consider it "blurry" or invalid.
        val brightness = calculateAverageBrightness(grayPixels)
        if (brightness < 10.0) return true
        
        return calculateLaplacianVariance(grayPixels, width, height) < threshold
    }
}
