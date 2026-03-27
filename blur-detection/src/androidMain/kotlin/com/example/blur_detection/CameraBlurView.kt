package com.example.blur_detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

/**
 * Enhanced CameraBlurView with configurable shake detection and prevention.
 *
 * @param onImageCaptured Callback when a valid image is captured
 * @param modifier Modifier for the view
 * @param config Configuration for blur and shake detection behavior
 * @param dialogTitle Title for blur detection dialog
 * @param dialogMessage Message for blur detection dialog
 * @param retakeButtonText Text for retake button
 * @param useAnywayButtonText Text for use anyway button
 */
@Composable
actual fun CameraBlurView(
    onImageCaptured: (ByteArray) -> Unit,
    onImageResult: (CapturedImageResult) -> Unit,
    modifier: Modifier,
    config: BlurDetectionConfig,
    dialogTitle: String,
    dialogMessage: String,
    retakeButtonText: String,
    useAnywayButtonText: String
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context) }

    var showBlurDialog by remember { mutableStateOf(false) }
    var lastCapturedImage by remember { mutableStateOf<ByteArray?>(null) }
    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isShaking by remember { mutableStateOf(false) }
    var blurCause by remember { mutableStateOf<String?>(null) }
    var lastImageResult by remember { mutableStateOf<CapturedImageResult?>(null) }

    // Initialize shake detector
    val shakeDetector = remember {
        ShakeDetector(context, config) { shaking ->
            isShaking = shaking
        }
    }

    // Start/stop shake detector with lifecycle
    DisposableEffect(Unit) {
        shakeDetector.start()
        onDispose {
            shakeDetector.stop()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        )

        // Shake warning indicator
        if (isShaking && config.showShakeWarning) {
            ShakeWarningBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp),
                mode = config.shakeCaptureMode
            )
        }

        // Capture Button
        CaptureButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            isCapturing = isCapturing,
            isShaking = isShaking,
            config = config,
            onClick = {
                // Check if capture should be prevented
                if (config.shouldPreventCapture(isShaking)) {
                    BlurDetectionLogger.debug("capture.prevented shaking=$isShaking mode=${config.shakeCaptureMode}")
                    return@CaptureButton
                }

                if (!isCapturing) {
                    isCapturing = true
                    val wasShakingDuringCapture = isShaking || shakeDetector.hadRecentMotion()
                    BlurDetectionLogger.debug(
                        "capture.started isShaking=$isShaking recentMotion=$wasShakingDuringCapture threshold=${config.blurThreshold}"
                    )
                    val mainExecutor = ContextCompat.getMainExecutor(context)

                    cameraController.takePicture(
                        mainExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val capturedPhoto = try {
                                    image.toCapturedPhoto()
                                } finally {
                                    image.close()
                                }

                                if (capturedPhoto == null) {
                                    BlurDetectionLogger.debug("capture.failed reason=image_conversion_null")
                                    isCapturing = false
                                    return
                                }

                                val effectiveThreshold = config.getEffectiveThreshold(wasShakingDuringCapture)
                                val blurAnalysis = BlurAnalyzer.analyze(
                                    bitmap = capturedPhoto.bitmap,
                                    config = config,
                                    threshold = effectiveThreshold
                                )
                                val blurred = blurAnalysis.isBlurred
                                val shouldShowDialog =
                                    config.shouldAutoReject(wasShakingDuringCapture) || blurred

                                BlurDetectionLogger.debug(
                                    "capture.analysis blurred=$blurred shouldShowDialog=$shouldShowDialog " +
                                        "autoReject=${config.shouldAutoReject(wasShakingDuringCapture)} " +
                                        "recentMotion=$wasShakingDuringCapture, reason=${blurAnalysis.reason}"
                                )

                                if (shouldShowDialog) {
                                    lastCapturedImage = capturedPhoto.bytes
                                    capturedImageBitmap = capturedPhoto.bitmap
                                    lastImageResult = CapturedImageResult(
                                        bytes = capturedPhoto.bytes,
                                        isBlurred = blurred,
                                        isSeverelyBlurred = blurAnalysis.isSeverelyBlurred,
                                        blurScore = blurAnalysis.variance,
                                        blurThreshold = blurAnalysis.effectiveThreshold,
                                        blurReason = blurAnalysis.reason,
                                        hadRecentMotion = wasShakingDuringCapture,
                                        averageEdgeEnergy = blurAnalysis.averageEdgeEnergy,
                                        directionalImbalance = blurAnalysis.directionalImbalance,
                                        normalizedSharpness = blurAnalysis.normalizedSharpness
                                    )
                                    blurCause = when {
                                        blurAnalysis.isSeverelyBlurred -> "blur_only_severe"
                                        wasShakingDuringCapture && blurred -> "shake_and_blur"
                                        wasShakingDuringCapture -> "shake_only"
                                        else -> "blur_only"
                                    }
                                    BlurDetectionLogger.debug(
                                        "capture.dialogShown blurCause=$blurCause variance=${blurAnalysis.variance} " +
                                            "threshold=${blurAnalysis.effectiveThreshold}"
                                    )
                                    showBlurDialog = true
                                } else {
                                    onImageCaptured(capturedPhoto.bytes)
                                    BlurDetectionLogger.debug(
                                        "capture.resultEmitted type=accepted_direct isBlurred=false reason=${blurAnalysis.reason}"
                                    )
                                    onImageResult(
                                        CapturedImageResult(
                                            bytes = capturedPhoto.bytes,
                                            isBlurred = false,
                                            isSeverelyBlurred = false,
                                            blurScore = blurAnalysis.variance,
                                            blurThreshold = blurAnalysis.effectiveThreshold,
                                            blurReason = blurAnalysis.reason,
                                            hadRecentMotion = wasShakingDuringCapture,
                                            averageEdgeEnergy = blurAnalysis.averageEdgeEnergy,
                                            directionalImbalance = blurAnalysis.directionalImbalance,
                                            normalizedSharpness = blurAnalysis.normalizedSharpness
                                        )
                                    )
                                }
                                isCapturing = false
                            }

                            override fun onError(exception: ImageCaptureException) {
                                BlurDetectionLogger.debug("capture.error message=${exception.message}")
                                isCapturing = false
                            }
                        }
                    )
                }
            }
        )

        if (showBlurDialog) {
            BlurDetectionDialog(
                blurCause = blurCause,
                capturedImageBitmap = capturedImageBitmap,
                dialogTitle = dialogTitle,
                dialogMessage = dialogMessage,
                retakeButtonText = retakeButtonText,
                useAnywayButtonText = useAnywayButtonText,
                allowUseAnyway = lastImageResult?.isSeverelyBlurred != true,
                onDismiss = {
                    BlurDetectionLogger.debug("capture.dialogDismissed action=dismiss")
                    showBlurDialog = false
                    lastCapturedImage = null
                    capturedImageBitmap = null
                    blurCause = null
                    lastImageResult = null
                },
                onRetake = {
                    BlurDetectionLogger.debug("capture.dialogDismissed action=retake")
                    showBlurDialog = false
                    lastCapturedImage = null
                    capturedImageBitmap = null
                    blurCause = null
                    lastImageResult = null
                },
                onUseAnyway = {
                    if (lastImageResult?.isSeverelyBlurred == true) {
                        BlurDetectionLogger.debug("capture.dialogDismissed action=use_anyway_blocked reason=severe_blur")
                        return@BlurDetectionDialog
                    }
                    BlurDetectionLogger.debug(
                        "capture.dialogDismissed action=use_anyway storedResultBlurred=${lastImageResult?.isBlurred} " +
                            "reason=${lastImageResult?.blurReason}"
                    )
                    showBlurDialog = false
                    lastCapturedImage?.let { bytes ->
                        onImageCaptured(bytes)
                        val resultToEmit = lastImageResult?.copyForAcceptedWarning(bytes = bytes)
                            ?: CapturedImageResult(
                                bytes = bytes,
                                isBlurred = true,
                                isSeverelyBlurred = false,
                                blurReason = "User accepted image after warning",
                                hadRecentMotion = isShaking || shakeDetector.hadRecentMotion()
                            )
                        BlurDetectionLogger.debug(
                            "capture.resultEmitted type=accepted_after_warning isBlurred=${resultToEmit.isBlurred} " +
                                "reason=${resultToEmit.blurReason}"
                        )
                        onImageResult(
                            resultToEmit
                        )
                    }
                    lastCapturedImage = null
                    capturedImageBitmap = null
                    blurCause = null
                    lastImageResult = null
                }
            )
        }
    }
}

@Composable
private fun ShakeWarningBanner(
    modifier: Modifier = Modifier,
    mode: ShakeCaptureMode
) {
    val warningText = when (mode) {
        ShakeCaptureMode.PREVENT_CAPTURE -> "Device is shaking - Capture disabled!"
        ShakeCaptureMode.AUTO_REJECT_ON_SHAKE -> "Device is shaking - Photos will be rejected!"
        ShakeCaptureMode.WARN_ONLY -> "Device is shaking - Hold steady for better quality"
        ShakeCaptureMode.SILENT_ADJUST -> "Device is shaking - Using stricter detection"
    }

    Card(
        modifier = modifier.fillMaxWidth(0.9f),
        backgroundColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.9f),
        elevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Shake warning",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                warningText,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
private fun CaptureButton(
    modifier: Modifier = Modifier,
    isCapturing: Boolean,
    isShaking: Boolean,
    config: BlurDetectionConfig,
    onClick: () -> Unit
) {
    val isEnabled = !isCapturing && !config.shouldPreventCapture(isShaking)

    val buttonText = when {
        isCapturing -> ""
        config.shouldPreventCapture(isShaking) -> "Hold Device Steady"
        isShaking && config.shakeCaptureMode == ShakeCaptureMode.WARN_ONLY -> "⚠️ May Be Blurry"
        else -> "Capture"
    }

    val buttonColor = when {
        !isEnabled -> androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.5f)
        isShaking -> androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.7f)
        else -> MaterialTheme.colors.primary
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .widthIn(min = 200.dp),
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = buttonColor,
            disabledBackgroundColor = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.5f)
        )
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = androidx.compose.ui.graphics.Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                buttonText,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.button
            )
        }
    }
}

@Composable
private fun BlurDetectionDialog(
    blurCause: String?,
    capturedImageBitmap: Bitmap?,
    dialogTitle: String,
    dialogMessage: String,
    retakeButtonText: String,
    useAnywayButtonText: String,
    allowUseAnyway: Boolean,
    onDismiss: () -> Unit,
    onRetake: () -> Unit,
    onUseAnyway: () -> Unit
) {
    val title = when (blurCause) {
        "shake_and_blur" -> "Motion Blur Detected"
        "shake_only" -> "Phone Was Moving"
        else -> dialogTitle
    }

    val message = when (blurCause) {
        "shake_and_blur" -> "The phone was shaking during capture and the image is blurry. Please hold the device steady and try again."
        "shake_only" -> "The phone was moving during capture. This may have caused blur. Would you like to retake?"
        "blur_only_severe" -> "The captured image is too blurred to use. Please retake the photo."
        else -> dialogMessage
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFFFFA726),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title)
            }
        },
        text = {
            Column {
                Text(message)
                Spacer(modifier = Modifier.height(16.dp))
                capturedImageBitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Image Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetake) {
                Text(retakeButtonText)
            }
        },
        dismissButton = if (allowUseAnyway) {
            {
                TextButton(onClick = onUseAnyway) {
                    Text(useAnywayButtonText)
                }
            }
        } else {
            null
        }
    )
}

private data class CapturedPhoto(
    val bytes: ByteArray,
    val bitmap: Bitmap
)

private fun ImageProxy.toCapturedPhoto(): CapturedPhoto? {
    val bitmap = when (format) {
        ImageFormat.JPEG -> decodeJpegBitmap()
        ImageFormat.YUV_420_888 -> decodeYuvBitmap()
        else -> null
    } ?: return null

    val rotatedBitmap = bitmap.rotate(rotationDegrees = imageInfo.rotationDegrees)
    val bytes = rotatedBitmap.toJpegByteArray() ?: return null
    return CapturedPhoto(bytes = bytes, bitmap = rotatedBitmap)
}

private fun ImageProxy.decodeJpegBitmap(): Bitmap? {
    val buffer = planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun ImageProxy.decodeYuvBitmap(): Bitmap? {
    val nv21 = yuv420888ToNv21() ?: return null
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val outputStream = ByteArrayOutputStream()
    val compressed = yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, outputStream)
    if (!compressed) {
        return null
    }
    val jpegBytes = outputStream.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}

private fun ImageProxy.yuv420888ToNv21(): ByteArray? {
    if (planes.size < 3) {
        return null
    }

    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)

    var offset = ySize
    val chromaRowStride = planes[1].rowStride
    val chromaPixelStride = planes[1].pixelStride
    val chromaHeight = height / 2
    val chromaWidth = width / 2

    val uBytes = ByteArray(uSize).also(uBuffer::get)
    val vBytes = ByteArray(vSize).also(vBuffer::get)

    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val index = row * chromaRowStride + col * chromaPixelStride
            if (index >= vBytes.size || index >= uBytes.size) {
                return null
            }
            nv21[offset++] = vBytes[index]
            nv21[offset++] = uBytes[index]
        }
    }

    return nv21
}

private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) {
        return this
    }

    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.toJpegByteArray(): ByteArray? {
    val outputStream = ByteArrayOutputStream()
    val compressed = compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
    if (!compressed) {
        return null
    }
    return outputStream.toByteArray()
}

private const val JPEG_QUALITY: Int = 95
private fun CapturedImageResult.copyForAcceptedWarning(bytes: ByteArray): CapturedImageResult {
    return CapturedImageResult(
        bytes = bytes,
        isBlurred = isBlurred,
        isSeverelyBlurred = isSeverelyBlurred,
        blurScore = blurScore,
        blurThreshold = blurThreshold,
        blurReason = "$blurReason (accepted after warning)",
        hadRecentMotion = hadRecentMotion,
        averageEdgeEnergy = averageEdgeEnergy,
        directionalImbalance = directionalImbalance,
        normalizedSharpness = normalizedSharpness
    )
}
