package com.example.blur_detection

import android.util.Log

internal object BlurDetectionLogger {
    const val TAG: String = "BlurDetectionDebug"

    fun debug(message: String) {
        Log.d(TAG, message)
    }
}
