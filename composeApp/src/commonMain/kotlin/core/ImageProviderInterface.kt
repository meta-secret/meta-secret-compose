package core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

interface ImageProviderInterface {
    @Composable
    fun getPainter(key: AppImage): Painter
}
