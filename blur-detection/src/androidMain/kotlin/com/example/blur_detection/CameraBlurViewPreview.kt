package com.example.blur_detection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A mock version of CameraBlurView for previews and testing.
 */
@Composable
fun CameraBlurViewPreviewContent(
    modifier: Modifier = Modifier,
    isCapturing: Boolean = false,
    showBlurDialog: Boolean = false,
    dialogTitle: String = "Blurry Image Detected",
    dialogMessage: String = "The image looks a bit blurry. Would you like to retake it?",
    retakeButtonText: String = "Retake",
    useAnywayButtonText: String = "Use Anyway"
) {
    Box(modifier = modifier.background(Color.Black)) {
        // Mock Camera Preview
        Text(
            "Camera Feed Placeholder",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )

        // Capture Button
        Button(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            enabled = !isCapturing
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Capture")
            }
        }

        if (showBlurDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(dialogTitle) },
                text = {
                    Column {
                        Text(dialogMessage)
                        Spacer(modifier = Modifier.height(16.dp))
                        // Mock Image Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Image Preview")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {}) {
                        Text(retakeButtonText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {}) {
                        Text(useAnywayButtonText)
                    }
                }
            )
        }
    }
}

@Preview
@Composable
fun CameraBlurViewPreview() {
    CameraBlurViewPreviewContent(modifier = Modifier.fillMaxSize())
}

@Preview
@Composable
fun CameraBlurViewBlurDialogPreview() {
    CameraBlurViewPreviewContent(
        modifier = Modifier.fillMaxSize(),
        showBlurDialog = true
    )
}
