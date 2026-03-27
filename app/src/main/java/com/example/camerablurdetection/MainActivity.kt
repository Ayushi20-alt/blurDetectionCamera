package com.example.camerablurdetection

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blur_detection.CapturedImageResult
import com.example.blur_detection.BlurDetectionConfig
import com.example.blur_detection.CameraBlurView
import com.example.blur_detection.ShakeCaptureMode
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var hasPermission by remember { mutableStateOf(false) }
                    var imageResult by remember { mutableStateOf<CapturedImageResult?>(null) }

                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        hasPermission = isGranted
                    }

                    LaunchedEffect(Unit) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasPermission) {
                            if (imageResult == null) {
                                CameraBlurView(
                                    onImageCaptured = {},
                                    onImageResult = { result ->
                                        imageResult = result
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    config = BlurDetectionConfig(
                                        blurThreshold = 13500.0,
                                        shakeThresholdMultiplier = 1.5,
                                        shakeAccelerationThreshold = 12f,
                                        shakeResetTimeMs = 650L,
                                        recentMotionWindowMs = 700L,
                                        normalizedSharpnessThreshold = 260.0,
                                        borderlineNormalizedSharpnessThreshold = 360.0,
                                        directionalSmearThreshold = 10.0,
                                        directionalImbalanceThreshold = 0.65,
                                        shakeCaptureMode = ShakeCaptureMode.WARN_ONLY
                                    )
                                )
                            } else {
                                val capturedImageResult = imageResult ?: return@Surface
                                val bitmap = BitmapFactory.decodeByteArray(
                                    capturedImageResult.bytes,
                                    0,
                                    capturedImageResult.bytes.size
                                )

                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, top = 12.dp, end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { imageResult = null }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back to camera"
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Captured Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentScale = ContentScale.Fit
                                        )

                                        Card {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val wasAcceptedAfterWarning =
                                                    capturedImageResult.blurReason?.contains(
                                                        other = "accepted after warning",
                                                        ignoreCase = true
                                                    ) == true
                                                val message = if (capturedImageResult.isBlurred && !wasAcceptedAfterWarning) {
                                                    "Image is blurred"
                                                } else if (wasAcceptedAfterWarning) {
                                                    "Accepted after warning"
                                                } else {
                                                    "Good image"
                                                }
                                                Text(
                                                    text = message,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Card {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Debug info",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text("Blurred: ${capturedImageResult.isBlurred}")
                                                Text(
                                                    "Variance: ${
                                                        capturedImageResult.blurScore?.let { formatDebugValue(it) } ?: "n/a"
                                                    }"
                                                )
                                                Text(
                                                    "Threshold: ${
                                                        capturedImageResult.blurThreshold?.let { formatDebugValue(it) } ?: "n/a"
                                                    }"
                                                )
                                                Text(
                                                    "Normalized sharpness: ${
                                                        capturedImageResult.normalizedSharpness?.let { formatDebugValue(it) } ?: "n/a"
                                                    }"
                                                )
                                                Text(
                                                    "Avg edge: ${
                                                        capturedImageResult.averageEdgeEnergy?.let { formatDebugValue(it) } ?: "n/a"
                                                    }"
                                                )
                                                Text(
                                                    "Directional imbalance: ${
                                                        capturedImageResult.directionalImbalance?.let { formatDebugValue(it) } ?: "n/a"
                                                    }"
                                                )
                                                Text("Recent motion: ${capturedImageResult.hadRecentMotion}")
                                                Text(
                                                    "Reason: ${capturedImageResult.blurReason ?: "No reason available"}"
                                                )
                                            }
                                        }

                                        if (capturedImageResult.isBlurred) {
                                            Button(
                                                onClick = { imageResult = null },
                                                modifier = Modifier.padding(bottom = 32.dp)
                                            ) {
                                                Text("Capture Again")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Camera permission is required", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}

private fun formatDebugValue(value: Double): String = String.format(Locale.US, "%.2f", value)
