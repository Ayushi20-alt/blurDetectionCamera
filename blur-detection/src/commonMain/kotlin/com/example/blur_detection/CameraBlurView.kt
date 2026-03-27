package com.example.blur_detection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class CapturedImageResult(
    val bytes: ByteArray,
    val isBlurred: Boolean,
    val isSeverelyBlurred: Boolean = false,
    val blurScore: Double? = null,
    val blurThreshold: Double? = null,
    val blurReason: String? = null,
    val hadRecentMotion: Boolean = false,
    val averageEdgeEnergy: Double? = null,
    val directionalImbalance: Double? = null,
    val normalizedSharpness: Double? = null
)

@Composable
expect fun CameraBlurView(
    onImageCaptured: (ByteArray) -> Unit,
    onImageResult: (CapturedImageResult) -> Unit = {},
    modifier: Modifier = Modifier,
    config: BlurDetectionConfig = BlurDetectionConfig(),
    dialogTitle: String = "Blurry Image Detected",
    dialogMessage: String = "The image looks a bit blurry. Would you like to retake it?",
    retakeButtonText: String = "Retake",
    useAnywayButtonText: String = "Use Anyway"
)
