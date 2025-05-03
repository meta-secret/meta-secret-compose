package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import qrscanner.CameraLens
import qrscanner.OverlayShape
import qrscanner.QrScanner

@Composable
fun QRScannerScreen(
    onResult: (String) -> Unit,
    onError: (String) -> Unit = {}
) {
    var resultReceived by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(360.dp)
            .clip(RoundedCornerShape(8.dp))
            .clipToBounds()
    ) {
        QrScanner(
            modifier = Modifier.fillMaxSize(),
            flashlightOn = false,
            cameraLens = CameraLens.Back,
            openImagePicker = false,
            onCompletion = { result ->
                if (!resultReceived) {
                    resultReceived = true
                    onResult(result)
                }
            },
            onFailure = { error ->
                onError(error)
            },
            overlayShape = OverlayShape.Rectangle,
            overlayColor = Color(0x88000000),
            overlayBorderColor = Color.White,
            customOverlay = { },
            imagePickerHandler = { },
            permissionDeniedView = { Text("Camera permission denied", color = Color.White) }
        )
    }
}