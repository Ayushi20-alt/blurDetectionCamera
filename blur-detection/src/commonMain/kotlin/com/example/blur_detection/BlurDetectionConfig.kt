package com.example.blur_detection

/**
 * Defines how the camera should behave when shake is detected.
 */
enum class ShakeCaptureMode {
    /**
     * Completely prevent photo capture while device is shaking.
     * Button becomes disabled with message "Hold Device Steady"
     */
    PREVENT_CAPTURE,

    /**
     * Allow capture but show warning on button: "⚠️ May Be Blurry"
     * Still performs blur detection after capture
     */
    WARN_ONLY,

    /**
     * Allow capture with increased blur sensitivity.
     * No visual warning, but threshold is adjusted
     */
    SILENT_ADJUST,

    /**
     * Automatically show blur dialog if shake detected during capture,
     * regardless of actual blur detection result
     */
    AUTO_REJECT_ON_SHAKE
}

/**
 * Updated configuration with shake capture mode.
 */
data class BlurDetectionConfig(
    val blurThreshold: Double = 300.0,
    val shakeThresholdMultiplier: Double = 1.5,
    val shakeAccelerationThreshold: Float = 15f,
    val shakeResetTimeMs: Long = 500L,
    val recentMotionWindowMs: Long = 1200L,

    /**
     * Determines behavior when shake is detected.
     * Default: PREVENT_CAPTURE (safest option)
     */
    val shakeCaptureMode: ShakeCaptureMode = ShakeCaptureMode.PREVENT_CAPTURE,

    val showShakeWarning: Boolean = true
)

fun BlurDetectionConfig.getEffectiveThreshold(isShaking: Boolean): Double {
    return if (isShaking) {
        blurThreshold * shakeThresholdMultiplier
    } else {
        blurThreshold
    }
}

fun BlurDetectionConfig.shouldPreventCapture(isShaking: Boolean): Boolean {
    return isShaking && shakeCaptureMode == ShakeCaptureMode.PREVENT_CAPTURE
}

fun BlurDetectionConfig.shouldAutoReject(isShaking: Boolean): Boolean {
    return isShaking && shakeCaptureMode == ShakeCaptureMode.AUTO_REJECT_ON_SHAKE
}
