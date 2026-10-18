package core

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
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
