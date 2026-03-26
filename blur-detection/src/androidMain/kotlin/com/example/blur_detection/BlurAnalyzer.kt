package com.example.blur_detection

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Locale
import kotlin.math.abs
import androidx.core.graphics.scale

internal object BlurAnalyzer {

    fun analyze(bitmap: Bitmap, threshold: Double): BlurAnalysisResult {
        // The longest side is scaled down to a maximum of 640px
        val scaledBitmap = bitmap.scaleForBlurAnalysis()
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val grayPixels = ByteArray(width * height)

        // The RGB pixels are converted to grayscale using the standard luminance formula:
        // $$Y = 0.299 \cdot R + 0.587 \cdot G + 0.114 \cdot B$$
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = scaledBitmap.getPixel(x, y)
                val grayscaleValue =
                    (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                grayPixels[y * width + x] = grayscaleValue.toByte()
            }
        }

        val variance = BlurDetector.calculateLaplacianVariance(grayPixels, width, height)
        val motionMetrics = calculateMotionMetrics(grayPixels, width, height)
        val effectiveThreshold = threshold * VARIANCE_THRESHOLD_MULTIPLIER
        val directionalSmear = minOf(motionMetrics.horizontalEnergy, motionMetrics.verticalEnergy)
        // Motion blur typically occurs in a specific direction
        val directionalImbalance = calculateDirectionalImbalance(
            horizontalEnergy = motionMetrics.horizontalEnergy,
            verticalEnergy = motionMetrics.verticalEnergy
        )
        // Because raw Laplacian variance can be distorted by lighting or image texture, the analyzer normalizes it:
        val normalizedSharpness = calculateNormalizedSharpness(
            variance = variance,
            averageEdgeEnergy = motionMetrics.averageEnergy
        )
        val isBlurred =
            normalizedSharpness < NORMALIZED_SHARPNESS_THRESHOLD ||
                (
                    normalizedSharpness < BORDERLINE_NORMALIZED_SHARPNESS_THRESHOLD &&
                        (
                            directionalSmear < DIRECTIONAL_SMEAR_THRESHOLD ||
                                directionalImbalance > DIRECTIONAL_IMBALANCE_THRESHOLD
                            )
                    )

        val reason = when {
            normalizedSharpness < NORMALIZED_SHARPNESS_THRESHOLD ->
                "Low normalized sharpness: ${normalizedSharpness.formatForDebug()} < ${NORMALIZED_SHARPNESS_THRESHOLD.formatForDebug()}"
            normalizedSharpness < BORDERLINE_NORMALIZED_SHARPNESS_THRESHOLD &&
                directionalSmear < DIRECTIONAL_SMEAR_THRESHOLD ->
                "Directional smear detected: ${directionalSmear.formatForDebug()} < ${DIRECTIONAL_SMEAR_THRESHOLD.formatForDebug()}"
            normalizedSharpness < BORDERLINE_NORMALIZED_SHARPNESS_THRESHOLD &&
                directionalImbalance > DIRECTIONAL_IMBALANCE_THRESHOLD ->
                "Directional imbalance too high: ${directionalImbalance.formatForDebug()} > ${DIRECTIONAL_IMBALANCE_THRESHOLD.formatForDebug()}"
            else -> "Image passed blur checks"
        }

        val result = BlurAnalysisResult(
            isBlurred = isBlurred,
            variance = variance,
            effectiveThreshold = effectiveThreshold,
            normalizedSharpness = normalizedSharpness,
            averageEdgeEnergy = motionMetrics.averageEnergy,
            horizontalEdgeEnergy = motionMetrics.horizontalEnergy,
            verticalEdgeEnergy = motionMetrics.verticalEnergy,
            directionalImbalance = directionalImbalance,
            reason = reason
        )

        BlurDetectionLogger.debug(
            "blurAnalysis isBlurred=${result.isBlurred}, variance=${result.variance.formatForDebug()}, " +
                "threshold=${result.effectiveThreshold.formatForDebug()}, normalized=${result.normalizedSharpness.formatForDebug()}, " +
                "avgEdge=${result.averageEdgeEnergy.formatForDebug()}, " +
                "horizontal=${result.horizontalEdgeEnergy.formatForDebug()}, vertical=${result.verticalEdgeEnergy.formatForDebug()}, " +
                "imbalance=${result.directionalImbalance.formatForDebug()}, " +
                "reason=${result.reason}"
        )

        return result
    }

    private fun Bitmap.scaleForBlurAnalysis(): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= TARGET_LONGEST_SIDE_PX) {
            return this
        }

        val scaleFactor = TARGET_LONGEST_SIDE_PX.toFloat() / longestSide.toFloat()
        val scaledWidth = (width * scaleFactor).toInt().coerceAtLeast(MIN_IMAGE_DIMENSION_PX)
        val scaledHeight = (height * scaleFactor).toInt().coerceAtLeast(MIN_IMAGE_DIMENSION_PX)
        return this.scale(scaledWidth, scaledHeight)
    }

    private fun calculateMotionMetrics(grayPixels: ByteArray, width: Int, height: Int): MotionMetrics {
        var totalEnergy = 0.0
        var horizontalEnergy = 0.0
        var verticalEnergy = 0.0
        val pixelCount = ((width - 2) * (height - 2)).coerceAtLeast(1)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val dx = abs(grayPixels[index + 1] - grayPixels[index - 1])
                val dy = abs(grayPixels[index + width] - grayPixels[index - width])
                horizontalEnergy += dx
                verticalEnergy += dy
                totalEnergy += dx + dy
            }
        }

        return MotionMetrics(
            averageEnergy = totalEnergy / pixelCount,
            horizontalEnergy = horizontalEnergy / pixelCount,
            verticalEnergy = verticalEnergy / pixelCount
        )
    }

    private fun calculateDirectionalImbalance(horizontalEnergy: Double, verticalEnergy: Double): Double {
        val maxEnergy = maxOf(horizontalEnergy, verticalEnergy)
        val minEnergy = minOf(horizontalEnergy, verticalEnergy)
        if (maxEnergy <= 0.0) {
            return 0.0
        }
        return (maxEnergy - minEnergy) / maxEnergy
    }

    private fun calculateNormalizedSharpness(variance: Double, averageEdgeEnergy: Double): Double {
        return variance / averageEdgeEnergy.coerceAtLeast(MIN_EDGE_ENERGY_FOR_NORMALIZATION)
    }
}

internal class BlurAnalysisResult(
    val isBlurred: Boolean,
    val variance: Double,
    val effectiveThreshold: Double,
    val normalizedSharpness: Double,
    val averageEdgeEnergy: Double,
    val horizontalEdgeEnergy: Double,
    val verticalEdgeEnergy: Double,
    val directionalImbalance: Double,
    val reason: String
)

private class MotionMetrics(
    val averageEnergy: Double,
    val horizontalEnergy: Double,
    val verticalEnergy: Double
)

private fun Double.formatForDebug(): String = String.format(Locale.US, "%.2f", this)

// The Decision Matrix :-

// Passes Threshold: If normalized sharpness is $\ge 420$ (BORDERLINE), it is considered sharp.
// Fails Hard Threshold: If normalized sharpness is $< 320$, it is considered blurry.
// The Borderline Zone ($320 - 420$): If the sharpness falls in this gray area, the algorithm checks for motion blur symptoms:
// If Directional Smear (the minimum of horizontal/vertical energy) is very low ($< 12.0$), it fails.
// If Directional Imbalance is very high ($> 0.55$), it fails.

private const val TARGET_LONGEST_SIDE_PX: Int = 640
private const val MIN_IMAGE_DIMENSION_PX: Int = 64
private const val VARIANCE_THRESHOLD_MULTIPLIER: Double = 1.0
private const val DIRECTIONAL_SMEAR_THRESHOLD: Double = 12.0
private const val DIRECTIONAL_IMBALANCE_THRESHOLD: Double = 0.55
private const val NORMALIZED_SHARPNESS_THRESHOLD: Double = 320.0
private const val BORDERLINE_NORMALIZED_SHARPNESS_THRESHOLD: Double = 420.0
private const val MIN_EDGE_ENERGY_FOR_NORMALIZATION: Double = 1.0
