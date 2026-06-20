package ui

import core.AppImage
import core.ImageProviderInterface
import androidx.compose.runtime.getValue
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
import org.koin.compose.koinInject

@Composable
fun AddButton(action: (Boolean) -> Unit) {
    val imageProvider: ImageProviderInterface = koinInject()
    val imgSize = 80
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp, end = 24.dp),
        Alignment.BottomEnd
    ) {
        Image(
            painter = imageProvider.getPainter(AppImage.AddButton),
            contentDescription = null,
            modifier = Modifier
                .size(imgSize.dp)
                .clickable { action(true) }
        )
    }
}


