package ui.dialogs.qrscanning

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.close
import org.jetbrains.compose.resources.painterResource
import ui.QRScannerScreen

@Composable
fun scanQRCode(isVisible: (Boolean) -> Unit, scannedText: (String) -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Image(
                painter = painterResource(Res.drawable.close),
                contentDescription = null,
                modifier = Modifier
                    .padding(16.dp)
                    .size(32.dp)
                    .align(Alignment.TopEnd)
                    .clickable { isVisible(false) }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
            ) {
                QRScannerScreen(
                    onResult = { isVisible(false); scannedText(it) }
                )
            }
        }
    }
}