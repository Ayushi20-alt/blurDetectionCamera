package com.example.blur_detection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.posix.memcpy
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap

@OptIn(ExperimentalForeignApi::class)
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
    val captureSession = remember { AVCaptureSession() }
    val photoOutput = remember { AVCapturePhotoOutput() }
    var showBlurDialog by remember { mutableStateOf(false) }
    var lastCapturedImage by remember { mutableStateOf<ByteArray?>(null) }
    var capturedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val photoCaptureDelegate = remember {
        object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
            override fun captureOutput(
                output: AVCapturePhotoOutput,
                didFinishProcessingPhoto: AVCapturePhoto,
                error: NSError?
            ) {
                if (error != null) {
                    isCapturing = false
                    return
                }
                
                val imageData = didFinishProcessingPhoto.fileDataRepresentation() ?: return
                val bytes = imageData.toByteArray()
                
                val uiImage = UIImage(data = imageData)
                val isBlurred = checkBlur(
                    uiImage = uiImage,
                    threshold = config.blurThreshold
                )
                
                if (isBlurred) {
                    lastCapturedImage = bytes
                    capturedImageBitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                    showBlurDialog = true
                } else {
                    onImageCaptured(bytes)
                    onImageResult(CapturedImageResult(bytes = bytes, isBlurred = false))
                }
                isCapturing = false
            }
        }
    }

    Box(modifier = modifier) {
        UIKitView(
            factory = {
                val container = UIView()
                val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                container.layer.addSublayer(previewLayer)
                
                val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
                if (device != null) {
                    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) as? AVCaptureDeviceInput
                    if (input != null && captureSession.canAddInput(input)) {
                        captureSession.addInput(input)
                    }
                }
                
                if (captureSession.canAddOutput(photoOutput)) {
                    captureSession.addOutput(photoOutput)
                }
                
                captureSession.startRunning()
                container
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val previewLayer = view.layer.sublayers?.firstOrNull() as? AVCaptureVideoPreviewLayer
                previewLayer?.frame = view.bounds
            }
        )

        Button(
            onClick = {
                if (!isCapturing) {
                    isCapturing = true
                    val settings = AVCapturePhotoSettings.photoSettings()
                    photoOutput.capturePhotoWithSettings(settings, photoCaptureDelegate)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            enabled = !isCapturing
        ) {
            if (isCapturing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Capture")
            }
        }

        if (showBlurDialog) {
            AlertDialog(
                onDismissRequest = { showBlurDialog = false },
                title = { Text(dialogTitle) },
                text = {
                    Column {
                        Text(dialogMessage)
                        Spacer(modifier = Modifier.height(16.dp))
                        capturedImageBitmap?.let { imageBitmap ->
                            Image(
                                bitmap = imageBitmap,
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
                    TextButton(onClick = { 
                        showBlurDialog = false
                        lastCapturedImage = null
                        capturedImageBitmap = null
                    }) {
                        Text(retakeButtonText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showBlurDialog = false
                        lastCapturedImage?.let {
                            onImageCaptured(it)
                            onImageResult(CapturedImageResult(bytes = it, isBlurred = true))
                        }
                        lastCapturedImage = null
                        capturedImageBitmap = null
                    }) {
                        Text(useAnywayButtonText)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val byteArray = ByteArray(size)
    if (size > 0) {
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return byteArray
}

@OptIn(ExperimentalForeignApi::class)
private fun checkBlur(uiImage: UIImage, threshold: Double): Boolean {
    val cgImage = uiImage.CGImage ?: return false
    val width = 480UL
    val height = 640UL
    val colorSpace = CGColorSpaceCreateDeviceGray()
    val bytesPerPixel = 1UL
    val bytesPerRow = bytesPerPixel * width
    val bitsPerComponent = 8UL
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value
    
    val context = CGBitmapContextCreate(
        null,
        width,
        height,
        bitsPerComponent,
        bytesPerRow,
        colorSpace,
        bitmapInfo
    ) ?: return false
    
    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
    val data = CGBitmapContextGetData(context) ?: return false
    
    val grayPixels = ByteArray((width * height).toInt())
    val rawData = data.reinterpret<ByteVar>()
    for (i in 0 until (width * height).toInt()) {
        grayPixels[i] = rawData[i]
    }
    
    CGContextRelease(context)
    
    return BlurDetector.isBlurred(grayPixels, width.toInt(), height.toInt(), threshold)
}
