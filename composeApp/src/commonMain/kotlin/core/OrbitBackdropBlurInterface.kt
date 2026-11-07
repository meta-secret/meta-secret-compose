package core

import androidx.compose.ui.graphics.ImageBitmap

/** Blurs a captured presentation backdrop; never captures or stores camera frames. */
interface OrbitBackdropBlur {
    /**
     * Copies [source] at ([paddingPx], [paddingPx]) physical pixels into a separate mutable CPU image with
     * transparent padding.
     * Output dimensions are source.width/height + 2 * paddingPx. The input remains caller-owned and unchanged;
     * the caller owns the returned image and must clear its pixels and release references after use.
     */
    fun paddedCopy(source: ImageBitmap, paddingPx: Int): ImageBitmap

    /** Returns the same image dimensions, with [radiusPx] expressed in physical pixels. */
    suspend fun blur(source: ImageBitmap, radiusPx: Float): ImageBitmap
}
