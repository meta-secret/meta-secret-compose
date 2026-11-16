package core

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect as AndroidRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** CPU blur remains available on API 25, where Compose RenderEffect blur is ignored. */
class OrbitBackdropBlurAndroid : OrbitBackdropBlur {
    override fun paddedCopy(source: ImageBitmap, paddingPx: Int): ImageBitmap {
        require(paddingPx >= 0)
        val readable = checkNotNull(source.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true))
        var output: Bitmap? = null
        try {
            val padded = try {
                val allocated = Bitmap.createBitmap(readable.width + paddingPx * 2, readable.height + paddingPx * 2,
                    Bitmap.Config.ARGB_8888)
                output = allocated
                AndroidCanvas(allocated).drawBitmap(readable, null, AndroidRect(paddingPx, paddingPx,
                    paddingPx + readable.width, paddingPx + readable.height), null)
                allocated
            } finally {
                try { readable.eraseColor(android.graphics.Color.TRANSPARENT) } finally { readable.recycle() }
            }
            val image = padded.asImageBitmap()
            output = null
            return image
        } finally {
            output?.let { incomplete -> try { incomplete.eraseColor(android.graphics.Color.TRANSPARENT) } finally {
                incomplete.recycle() } }
        }
    }

    override suspend fun blur(source: ImageBitmap, radiusPx: Float): ImageBitmap =
        withContext(Dispatchers.Default) {
            require(radiusPx.isFinite() && radiusPx >= 0f && radiusPx <= 256f)
            if (radiusPx == 0f) return@withContext source
            val context = currentCoroutineContext()
            context.ensureActive()
            val bitmap = checkNotNull(source.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false))
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            try {
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            } finally {
                bitmap.recycle()
            }
            val plane = BlurPixels(pixels, width, height)
            blurPixels(plane, radiusPx, context)
            context.ensureActive()
            Bitmap.createBitmap(plane.pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
}

private class BlurPixels(val pixels: IntArray, val width: Int, val height: Int)

private fun blurPixels(image: BlurPixels, sigma: Float, context: CoroutineContext) {
    // Three box passes approximate a Gaussian in linear time, independent of its radius.
    // Premultiply before filtering to avoid colored halos at translucent edges.
    image.pixels.indices.forEach { index ->
        if (index % image.width == 0) context.ensureActive()
        val color = image.pixels[index]
        val alpha = color ushr 24
        image.pixels[index] = (alpha shl 24) or
            ((((color ushr 16) and 255) * alpha / 255) shl 16) or
            ((((color ushr 8) and 255) * alpha / 255) shl 8) or
            ((color and 255) * alpha / 255)
    }
    val scratch = IntArray(image.pixels.size)
    val idealWidth = sqrt(4f * sigma * sigma + 1f)
    val lower = floor(idealWidth).toInt().let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(1)
    val lowerCount = ((12f * sigma * sigma - 3 * lower * lower - 12 * lower - 9) /
        (-4f * lower - 4f)).roundToInt().coerceIn(0, 3)
    repeat(3) { pass ->
        val radius = ((if (pass < lowerCount) lower else lower + 2) - 1) / 2
        boxPass(image, scratch, radius, true, context)
        boxPass(BlurPixels(scratch, image.width, image.height), image.pixels, radius, false, context)
    }
    image.pixels.indices.forEach { index ->
        if (index % image.width == 0) context.ensureActive()
        val color = image.pixels[index]
        val alpha = color ushr 24
        image.pixels[index] = if (alpha == 0) 0 else (alpha shl 24) or
            ((((color ushr 16) and 255) * 255 / alpha).coerceAtMost(255) shl 16) or
            ((((color ushr 8) and 255) * 255 / alpha).coerceAtMost(255) shl 8) or
            (((color and 255) * 255 / alpha).coerceAtMost(255))
    }
}

private fun boxPass(
    source: BlurPixels,
    destination: IntArray,
    radius: Int,
    horizontal: Boolean,
    context: CoroutineContext,
) {
    val lines = if (horizontal) source.height else source.width
    val length = if (horizontal) source.width else source.height
    val step = if (horizontal) 1 else source.width
    val divisor = radius * 2 + 1
    repeat(lines) { line ->
        context.ensureActive()
        val origin = if (horizontal) line * source.width else line
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = source.pixels[origin + offset.coerceIn(0, length - 1) * step]
            alpha += color ushr 24
            red += (color ushr 16) and 255
            green += (color ushr 8) and 255
            blue += color and 255
        }
        repeat(length) { index ->
            destination[origin + index * step] = ((alpha / divisor) shl 24) or
                ((red / divisor) shl 16) or ((green / divisor) shl 8) or (blue / divisor)
            val leaving = source.pixels[origin + (index - radius).coerceIn(0, length - 1) * step]
            val entering = source.pixels[origin + (index + radius + 1).coerceIn(0, length - 1) * step]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering ushr 16) and 255) - ((leaving ushr 16) and 255)
            green += ((entering ushr 8) and 255) - ((leaving ushr 8) and 255)
            blue += (entering and 255) - (leaving and 255)
        }
    }
}
