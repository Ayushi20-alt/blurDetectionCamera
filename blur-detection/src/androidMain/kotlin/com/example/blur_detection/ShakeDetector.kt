package com.example.blur_detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects shake gestures using the device accelerometer.
 *
 * Usage:
 * val shakeDetector = ShakeDetector(context, config) { isShaking ->
 *     // Handle shake state change
 * }
 * shakeDetector.start()
 * // Later: shakeDetector.stop()
 */
class ShakeDetector(
    context: Context,
    config: BlurDetectionConfig = BlurDetectionConfig(),
    private val onShakeStateChanged: (Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Shake detection parameters from config
    private val shakeThreshold = config.shakeAccelerationThreshold
    private val shakeResetTime = config.shakeResetTimeMs
    private val recentMotionWindowMs = config.recentMotionWindowMs

    private var lastShakeTime = 0L
    private var isCurrentlyShaking = false
    private var filteredAcceleration = SensorManager.GRAVITY_EARTH
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var hasLastReading = false

    fun start() {
        accelerometer?.let {
            BlurDetectionLogger.debug(
                "shake.start threshold=$shakeThreshold resetMs=$shakeResetTime recentMotionWindowMs=$recentMotionWindowMs"
            )
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        BlurDetectionLogger.debug("shake.stop")
        isCurrentlyShaking = false
        filteredAcceleration = SensorManager.GRAVITY_EARTH
        lastX = 0f
        lastY = 0f
        lastZ = 0f
        hasLastReading = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val accelerationMagnitude = sqrt(x * x + y * y + z * z)
            filteredAcceleration =
                MOTION_ALPHA * filteredAcceleration + (1 - MOTION_ALPHA) * accelerationMagnitude
            val linearAcceleration = abs(accelerationMagnitude - filteredAcceleration)

            val directionalMotion = if (hasLastReading) {
                abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
            } else {
                0f
            }

            lastX = x
            lastY = y
            lastZ = z
            hasLastReading = true

            val currentTime = System.currentTimeMillis()

            if (
                linearAcceleration > shakeThreshold ||
                directionalMotion > shakeThreshold * DIRECTIONAL_MOTION_MULTIPLIER
            ) {
                lastShakeTime = currentTime
                BlurDetectionLogger.debug(
                    "shake.motionDetected linear=${linearAcceleration.formatForLog()} directional=${directionalMotion.formatForLog()} " +
                        "threshold=${shakeThreshold.formatForLog()} currentlyShaking=$isCurrentlyShaking"
                )
                if (!isCurrentlyShaking) {
                    isCurrentlyShaking = true
                    BlurDetectionLogger.debug("shake.stateChanged shaking=true")
                    onShakeStateChanged(true)
                }
            } else if (isCurrentlyShaking && (currentTime - lastShakeTime) > shakeResetTime) {
                isCurrentlyShaking = false
                BlurDetectionLogger.debug("shake.stateChanged shaking=false")
                onShakeStateChanged(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    fun isShaking(): Boolean = isCurrentlyShaking

    fun hadRecentMotion(): Boolean {
        val currentTime = System.currentTimeMillis()
        val hadRecentMotion = (currentTime - lastShakeTime) <= recentMotionWindowMs
        BlurDetectionLogger.debug(
            "shake.hadRecentMotion result=$hadRecentMotion elapsedMs=${currentTime - lastShakeTime} windowMs=$recentMotionWindowMs"
        )
        return hadRecentMotion
    }
}

private const val MOTION_ALPHA: Float = 0.8f
private const val DIRECTIONAL_MOTION_MULTIPLIER: Float = 1.25f

private fun Float.formatForLog(): String = String.format(java.util.Locale.US, "%.2f", this)
