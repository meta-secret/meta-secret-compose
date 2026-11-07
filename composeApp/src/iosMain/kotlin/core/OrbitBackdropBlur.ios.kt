package core

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint as ImagePaint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.impl.use

/** Uses the Skia renderer already bundled with Compose for iOS. */
class OrbitBackdropBlurIos : OrbitBackdropBlur {
    override fun paddedCopy(source: ImageBitmap, paddingPx: Int): ImageBitmap {
        require(paddingPx >= 0)
        val output = ImageBitmap(source.width + paddingPx * 2, source.height + paddingPx * 2)
        var completed = false
        try {
            Canvas(output).drawImage(source, Offset(paddingPx.toFloat(), paddingPx.toFloat()), ImagePaint())
            completed = true
            return output
        } finally {
            if (!completed) try {
                Canvas(output).drawRect(Rect(0f, 0f, output.width.toFloat(), output.height.toFloat()), ImagePaint()
                    .apply { blendMode = BlendMode.Clear })
            } finally { output.asSkiaBitmap().close() }
        }
    }

    override suspend fun blur(source: ImageBitmap, radiusPx: Float): ImageBitmap =
        withContext(Dispatchers.Default) {
            require(radiusPx.isFinite() && radiusPx >= 0f && radiusPx <= 256f)
            if (radiusPx == 0f) return@withContext source
            currentCoroutineContext().ensureActive()
            val output = ImageBitmap(source.width, source.height)
            Image.makeFromBitmap(source.asSkiaBitmap()).use { image ->
                ImageFilter.makeBlur(radiusPx, radiusPx, FilterTileMode.CLAMP).use { filter ->
                    Paint().use { paint ->
                        paint.imageFilter = filter
                        Canvas(output).nativeCanvas.drawImage(image, 0f, 0f, paint)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            output
        }
}
