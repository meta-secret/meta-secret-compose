package core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource

class ImageProvider : ImageProviderInterface {
    @Composable
    override fun getPainter(key: AppImage): Painter = painterResource(key.resource())
}
